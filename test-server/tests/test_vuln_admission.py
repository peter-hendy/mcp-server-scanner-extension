from __future__ import annotations

import pytest

from tests.conftest import ServerFactory
from tests.support import tool_text

HOSTILE = (
    ("origin_admission", {"origin": "http://evil.example"}),
    ("origin_admission", {"origin": "null"}),
    ("origin_admission", {"origin": "http://attacker.example:1337"}),
    ("host_admission", {"host": "attacker.example"}),
)


@pytest.mark.parametrize(("vuln", "headers"), HOSTILE)
def test_hostile_header_executes_only_when_vulnerable(
    make_server: ServerFactory, vuln: str, headers: dict[str, str]
) -> None:
    vulnerable = make_server(vulns={"origin_admission": "safe", "host_admission": "safe", vuln: "vulnerable"}).session()
    assert tool_text(vulnerable.call_tool("echo", {"message": "m"}, **headers)) == "m"
    safe = make_server(vulns={"origin_admission": "safe", "host_admission": "safe"}).session()
    response = safe.post(safe.message("tools/call", {"name": "echo", "arguments": {"message": "m"}}), headers)
    assert response.status_code == 403


def test_safe_admits_allowed_origin_and_host(make_server: ServerFactory) -> None:
    safe = make_server(vulns={"origin_admission": "safe", "host_admission": "safe"}).session()
    assert tool_text(safe.call_tool("echo", {"message": "m"}, origin="http://localhost:3000")) == "m"
    assert tool_text(safe.call_tool("echo", {"message": "m"}, host="127.0.0.1:8000")) == "m"


def test_combined_host_and_origin(make_server: ServerFactory) -> None:
    headers = {"host": "attacker.example", "origin": "http://attacker.example"}
    vulnerable = make_server().session()
    assert tool_text(vulnerable.call_tool("echo", {"message": "m"}, **headers)) == "m"
    only_origin_fixed = make_server(vulns={"origin_admission": "safe"}).session()
    message = only_origin_fixed.message("tools/call", {"name": "echo", "arguments": {"message": "m"}})
    assert only_origin_fixed.post(message, headers).status_code == 403


@pytest.mark.parametrize("headers", [{"origin": "http://["}, {"host": "["}])
def test_malformed_headers_are_refused_not_crashed(make_server: ServerFactory, headers: dict[str, str]) -> None:
    server = make_server(vulns={"origin_admission": "safe", "host_admission": "safe"})
    session = server.session()
    response = session.post(session.message("ping"), headers)
    assert response.status_code == 403
