from __future__ import annotations

import pathlib
import shutil
import threading
import time

import pytest

from tests.conftest import ServerFactory
from tests.support import McpSession, tool_text

SHELL_PAYLOADS = (
    "127.0.0.1; printf mcp%s $((20+22)) #",
    "127.0.0.1$(printf mcp%s $((20+22)))",
    "127.0.0.1`printf mcp%s $((20+22))`",
    "127.0.0.1\nprintf mcp%s $((20+22))",
)


def _session(make_server: ServerFactory, vuln: str, mode: str) -> McpSession:
    return make_server(vulns={vuln: mode}, command_timeout=10).session()


@pytest.mark.parametrize("payload", SHELL_PAYLOADS)
def test_shell_injection(make_server: ServerFactory, payload: str) -> None:
    vulnerable = _session(make_server, "shell_command_injection", "vulnerable")
    assert "mcp42" in tool_text(vulnerable.call_tool("ping_host", {"host": payload}))
    safe = _session(make_server, "shell_command_injection", "safe")
    result = safe.call_tool("ping_host", {"host": payload})
    assert "mcp42" not in tool_text(result)
    assert result["isError"] is True


def test_shell_quoted_control_returns_literal(make_server: ServerFactory) -> None:
    vulnerable = _session(make_server, "shell_command_injection", "vulnerable")
    assert "$((20+22))" in tool_text(vulnerable.call_tool("ping_host", {"host": "'$((20+22))'"}))


def test_blind_shell_injection_runs_the_command_but_returns_no_output(
    make_server: ServerFactory, tmp_path: pathlib.Path
) -> None:
    marker = tmp_path / "ran"
    payload = f"nightly; touch {marker} #"
    vulnerable = _session(make_server, "blind_shell_command_injection", "vulnerable")
    result = vulnerable.call_tool("archive_logs", {"label": "nightly; printf mcp%s $((20+22)) #"})
    assert tool_text(result) == "archive queued"
    vulnerable.call_tool("archive_logs", {"label": payload})
    assert marker.exists()


def test_blind_shell_injection_safe_mode_rejects_metacharacters(
    make_server: ServerFactory, tmp_path: pathlib.Path
) -> None:
    marker = tmp_path / "ran"
    safe = _session(make_server, "blind_shell_command_injection", "safe")
    rejected = safe.call_tool("archive_logs", {"label": f"nightly; touch {marker} #"})
    assert rejected["isError"] is True
    assert not marker.exists()
    assert tool_text(safe.call_tool("archive_logs", {"label": "nightly"})) == "archive queued"


def test_python_eval_injection(make_server: ServerFactory) -> None:
    payload = "__import__('builtins').bytes([109, 99, 112, 52, 50]).decode()"
    vulnerable = _session(make_server, "python_eval_injection", "vulnerable")
    assert tool_text(vulnerable.call_tool("calculate", {"expression": payload})) == "mcp42"
    safe = _session(make_server, "python_eval_injection", "safe")
    result = safe.call_tool("calculate", {"expression": payload})
    assert result["isError"] is True
    assert "mcp42" not in tool_text(result)
    assert tool_text(safe.call_tool("calculate", {"expression": "6*7"})) == "42"


@pytest.mark.skipif(shutil.which("node") is None, reason="node is not installed")
def test_javascript_eval_injection(make_server: ServerFactory) -> None:
    payload = "Hi ${require('child_process').execSync('printf mcp%s 42').toString()}"
    vulnerable = _session(make_server, "javascript_eval_injection", "vulnerable")
    assert tool_text(vulnerable.call_tool("render_greeting", {"template": payload})) == "Hi mcp42"
    safe = _session(make_server, "javascript_eval_injection", "safe")
    assert "mcp42" not in tool_text(safe.call_tool("render_greeting", {"template": payload}))
    assert tool_text(safe.call_tool("render_greeting", {"template": "Hello ${name}"})) == "Hello world"


def test_eval_does_not_block_other_requests(make_server: ServerFactory) -> None:
    server = make_server(vulns={"python_eval_injection": "vulnerable"}, command_timeout=10)
    sleeper = server.session()
    worker = threading.Thread(
        target=sleeper.call_tool, args=("calculate", {"expression": "__import__('time').sleep(2)"})
    )
    worker.start()
    time.sleep(0.3)
    started = time.monotonic()
    assert tool_text(server.session().call_tool("echo", {"message": "fast"})) == "fast"
    assert time.monotonic() - started < 1.0
    worker.join()


def test_eval_is_bounded_by_command_timeout(make_server: ServerFactory) -> None:
    session = make_server(vulns={"python_eval_injection": "vulnerable"}, command_timeout=1).session()
    assert tool_text(session.call_tool("calculate", {"expression": "[0 for _ in iter(int, 1)]"})) == "command timed out"
