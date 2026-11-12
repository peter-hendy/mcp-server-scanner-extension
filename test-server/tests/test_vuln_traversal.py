from __future__ import annotations

from collections.abc import Callable
from typing import Any

import pytest

from tests.conftest import ServerFactory
from tests.support import McpSession, TestServer, prompt_text, resource_text, tool_text

DEEP = "../" * 16
SECRET = "OUTSIDE-ROOT-SECRET"
Reader = Callable[[McpSession, str, str], str]


def _tool(session: McpSession, variant: str, path: str) -> str:
    return tool_text(session.call_tool(f"read_file_{variant}", {"path": path}))


def _header(session: McpSession, variant: str, path: str) -> str:
    return tool_text(session.call_tool(f"read_document_{variant}", {}, **{"x-document-path": path}))


def _prompt(session: McpSession, variant: str, path: str) -> str:
    return prompt_text(session.result("prompts/get", {"name": f"review_file_{variant}", "arguments": {"path": path}}))


def _resource(session: McpSession, variant: str, path: str) -> str:
    return resource_text(session.result("resources/read", {"uri": _uri(session, variant, path)}))


def _uri(session: McpSession, variant: str, path: str) -> str:
    if variant == "encoded":
        return f"encoded-file:///{path}"
    scheme = {"plain": "file", "prefix": "prefix-file"}[variant]
    root = session.client.get("/_test/settings").json()["files_root"]
    return f"{scheme}://{root}/{path}"


SURFACES = {"tool": _tool, "header": _header, "prompt": _prompt, "resource": _resource}
VARIANT_PAYLOADS = {
    ("traversal_plain", "plain"): "../outside-secret.txt",
    ("traversal_encoded", "encoded"): "%2e%2e%2foutside-secret.txt",
}


def _server(make_server: ServerFactory, vuln: str, mode: str) -> TestServer:
    return make_server(vulns={vuln: mode})


@pytest.mark.parametrize("surface", SURFACES)
@pytest.mark.parametrize(("vuln", "variant"), VARIANT_PAYLOADS)
def test_escape_reads_outside_root_only_when_vulnerable(
    make_server: ServerFactory, surface: str, vuln: str, variant: str
) -> None:
    payload = VARIANT_PAYLOADS[(vuln, variant)]
    read: Reader = SURFACES[surface]
    assert SECRET in read(_server(make_server, vuln, "vulnerable").session(), variant, payload)
    assert SECRET not in read(_server(make_server, vuln, "safe").session(), variant, payload)


@pytest.mark.parametrize("surface", ["tool", "header", "prompt"])
def test_plain_reads_etc_passwd(make_server: ServerFactory, surface: str) -> None:
    read: Reader = SURFACES[surface]
    for payload in (DEEP + "etc/passwd", "/etc/passwd", "file:///etc/passwd"):
        assert "root:" in read(_server(make_server, "traversal_plain", "vulnerable").session(), "plain", payload)
        assert "root:" not in read(_server(make_server, "traversal_plain", "safe").session(), "plain", payload)


def test_plain_resource_template_reads_etc_passwd(make_server: ServerFactory) -> None:
    vulnerable = _server(make_server, "traversal_plain", "vulnerable").session()
    assert "root:" in _resource(vulnerable, "plain", DEEP + "etc/passwd")
    safe = _server(make_server, "traversal_plain", "safe").session()
    assert "Access denied" in _resource(safe, "plain", DEEP + "etc/passwd")


def test_encoded_rejects_literal_traversal_in_both_modes(make_server: ServerFactory) -> None:
    for mode in ("vulnerable", "safe"):
        text = _tool(_server(make_server, "traversal_encoded", mode).session(), "encoded", "../outside-secret.txt")
        assert text.startswith("Error: Access denied")


def test_in_root_reads_work_in_both_modes(make_server: ServerFactory) -> None:
    for vuln, variant in (("traversal_plain", "plain"), ("traversal_encoded", "encoded"),
                          ("traversal_prefix_sibling", "prefix")):
        for mode in ("vulnerable", "safe"):
            session = _server(make_server, vuln, mode).session()
            assert "Workspace readme" in _tool(session, variant, "readme.txt")
            assert "Workspace readme" in _resource(session, variant, "readme.txt")
            listed = [entry["uri"] for entry in _all_resources(session)]
            assert _uri(session, variant, "readme.txt") in listed


def _all_resources(session: McpSession) -> list[dict[str, Any]]:
    resources: list[dict[str, Any]] = []
    params: dict[str, Any] = {}
    while True:
        result = session.result("resources/list", params)
        resources += result["resources"]
        if "nextCursor" not in result:
            return resources
        params = {"cursor": result["nextCursor"]}


@pytest.mark.parametrize("uri", ["file:///etc/passwd", "file:///" + DEEP + "etc/passwd"])
def test_plain_absolute_resource_uri(make_server: ServerFactory, uri: str) -> None:
    vulnerable = _server(make_server, "traversal_plain", "vulnerable").session()
    assert "root:" in resource_text(vulnerable.result("resources/read", {"uri": uri}))
    safe = _server(make_server, "traversal_plain", "safe").session()
    assert "Access denied" in resource_text(safe.result("resources/read", {"uri": uri}))


def _root(server: TestServer) -> str:
    return str(server.client.get("/_test/settings").json()["files_root"])


@pytest.mark.parametrize("surface", ["tool", "header", "prompt"])
def test_prefix_sibling_error_differential(make_server: ServerFactory, surface: str) -> None:
    read: Reader = SURFACES[surface]
    vulnerable = _server(make_server, "traversal_prefix_sibling", "vulnerable")
    root = _root(vulnerable)
    control = read(vulnerable.session(), "prefix", "/bs2-canary/missing")
    assert f"Error: Access denied - path outside allowed directories: /bs2-canary/missing not in {root}" in control
    sibling = read(vulnerable.session(), "prefix", f"{root}_bs2_canary/missing")
    assert f"Error: Parent directory does not exist: {root}_bs2_canary" in sibling
    safe = _server(make_server, "traversal_prefix_sibling", "safe")
    safe_sibling = read(safe.session(), "prefix", f"{_root(safe)}_bs2_canary/missing")
    assert "Access denied" in safe_sibling


def test_tool_errors_are_flagged(make_server: ServerFactory) -> None:
    session = _server(make_server, "traversal_plain", "safe").session()
    result: dict[str, Any] = session.call_tool("read_file_plain", {"path": "/etc/passwd"})
    assert result["isError"] is True
