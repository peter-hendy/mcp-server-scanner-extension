from __future__ import annotations

import pytest

from tests.conftest import ServerFactory
from tests.support import McpSession, tool_text

MARKER = "downstream-protected-marker-4931"


def _session(make_server: ServerFactory, mode: str) -> McpSession:
    return make_server(vulns={"downstream_route_escape": mode}).session()


@pytest.mark.parametrize(
    "payload", ["../token", "%2e%2e%2ftoken", "../../v1/token", "../../../api/v1/admin/users", "LIC-1001/../../token"]
)
def test_route_escape_reaches_protected_route_only_when_vulnerable(make_server: ServerFactory, payload: str) -> None:
    vulnerable = _session(make_server, "vulnerable").call_tool("get_license", {"license_id": payload})
    assert MARKER in tool_text(vulnerable)
    assert vulnerable["isError"] is False
    safe = _session(make_server, "safe").call_tool("get_license", {"license_id": payload})
    assert MARKER not in tool_text(safe)


@pytest.mark.parametrize("mode", ["vulnerable", "safe"])
def test_normal_and_missing_controls_are_non_error_without_marker(make_server: ServerFactory, mode: str) -> None:
    session = _session(make_server, mode)
    normal = session.call_tool("get_license", {"license_id": "LIC-1001"})
    assert '"status": "active"' in tool_text(normal)
    missing = session.call_tool("get_license", {"license_id": "__mcp_route_missing_1__"})
    assert missing["isError"] is False
    assert MARKER not in tool_text(missing)


def test_downstream_api_served_on_same_host(make_server: ServerFactory) -> None:
    client = make_server().client
    assert MARKER in client.get("/downstream/api/v1/token").text
    assert client.get("/downstream/api/v1/licenses/LIC-1002").json()["status"] == "expired"
    assert client.get("/downstream/api/v1/nothing").status_code == 404
