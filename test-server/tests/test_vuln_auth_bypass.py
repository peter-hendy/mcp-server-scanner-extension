from __future__ import annotations

import pytest

from tests.conftest import ServerFactory
from tests.support import McpSession, TestServer, tool_text


def _strict(make_server: ServerFactory, **vulns: str) -> TestServer:
    return make_server(default_mode="safe", vulns=vulns)


def _anonymous_echo(server: TestServer, **headers: str) -> int:
    session = server.session(headers, initialize=False)
    initialized = session.initialize()
    if initialized.status_code != 200:
        return initialized.status_code
    return session.post(session.message("tools/call", {"name": "echo", "arguments": {"message": "x"}})).status_code


@pytest.mark.parametrize("header", ["x-forwarded-for", "x-real-ip"])
def test_loopback_forwarded_header_admits_anonymous_callers_only_when_vulnerable(
    make_server: ServerFactory, header: str
) -> None:
    vulnerable = _strict(make_server, auth_forwarded_loopback_trust="vulnerable")
    assert _anonymous_echo(vulnerable, **{header: "127.0.0.1"}) == 200
    assert _anonymous_echo(vulnerable, **{header: "203.0.113.7"}) == 401
    assert _anonymous_echo(vulnerable) == 401
    safe = _strict(make_server)
    assert _anonymous_echo(safe, **{header: "127.0.0.1"}) == 401


def test_forwarded_chain_is_judged_by_its_first_hop(make_server: ServerFactory) -> None:
    vulnerable = _strict(make_server, auth_forwarded_loopback_trust="vulnerable")
    assert _anonymous_echo(vulnerable, **{"x-forwarded-for": "127.0.0.1, 203.0.113.7"}) == 200
    assert _anonymous_echo(vulnerable, **{"x-forwarded-for": "203.0.113.7, 127.0.0.1"}) == 401


def _authenticated_session(server: TestServer) -> str:
    session = server.session({"authorization": f"Bearer {server.token()}"})
    assert tool_text(session.call_tool("echo", {"message": "ok"})) == "ok"
    return session.headers["mcp-session-id"]


def _session_only_echo(server: TestServer, session_id: str) -> int:
    session = server.session({"mcp-session-id": session_id}, initialize=False)
    return session.post(session.message("tools/call", {"name": "echo", "arguments": {"message": "x"}})).status_code


def test_session_used_with_a_valid_token_authenticates_alone_only_when_vulnerable(make_server: ServerFactory) -> None:
    vulnerable = _strict(make_server, auth_session_credential="vulnerable")
    assert _session_only_echo(vulnerable, _authenticated_session(vulnerable)) == 200
    safe = _strict(make_server)
    assert _session_only_echo(safe, _authenticated_session(safe)) == 401


def test_anonymous_and_unknown_sessions_never_authenticate(make_server: ServerFactory) -> None:
    vulnerable = _strict(make_server, auth_session_credential="vulnerable")
    assert _session_only_echo(vulnerable, "never-issued") == 401
    assert _anonymous_echo(vulnerable) == 401


def test_bypass_modules_leave_valid_tokens_working(make_server: ServerFactory) -> None:
    server = _strict(make_server, auth_forwarded_loopback_trust="vulnerable", auth_session_credential="vulnerable")
    _authenticated_session(server)


def _echo_with_bearer(server: TestServer, token: str) -> int:
    return _anonymous_echo(server, authorization=f"Bearer {token}")


def test_any_non_empty_bearer_is_accepted_only_when_vulnerable(make_server: ServerFactory) -> None:
    vulnerable = _strict(make_server, auth_presence_only_credential="vulnerable")
    assert _echo_with_bearer(vulnerable, "not_a_real_token_12345") == 200
    assert _anonymous_echo(vulnerable) == 401
    assert _anonymous_echo(vulnerable, authorization="Bearer ") == 401
    safe = _strict(make_server)
    assert _echo_with_bearer(safe, "not_a_real_token_12345") == 401


def test_presence_only_module_leaves_valid_tokens_working(make_server: ServerFactory) -> None:
    _authenticated_session(_strict(make_server, auth_presence_only_credential="vulnerable"))


def _anonymous_list_on(server: TestServer, path: str) -> int:
    session = McpSession(server.client, path=path)
    initialized = session.initialize()
    if initialized.status_code != 200:
        return initialized.status_code
    return session.post(session.message("tools/list", {})).status_code


def test_sibling_message_route_lists_tools_without_credentials_only_when_vulnerable(
    make_server: ServerFactory,
) -> None:
    vulnerable = _strict(make_server, auth_sibling_route="vulnerable")
    assert _anonymous_list_on(vulnerable, "/message") == 200
    assert _anonymous_list_on(vulnerable, "/mcp") == 401
    safe = _strict(make_server)
    assert _anonymous_list_on(safe, "/message") == 401
    assert _anonymous_list_on(safe, "/mcp") == 401


def test_sibling_message_route_serves_the_same_catalog_as_the_main_route(make_server: ServerFactory) -> None:
    server = _strict(make_server, auth_sibling_route="vulnerable")
    authenticated = server.session({"authorization": f"Bearer {server.token()}"})
    sibling = McpSession(server.client, path="/message")
    sibling.initialize()
    main_tools = authenticated.post(authenticated.message("tools/list", {})).json()["result"]["tools"]
    sibling_tools = sibling.post(sibling.message("tools/list", {})).json()["result"]["tools"]
    assert [tool["name"] for tool in sibling_tools] == [tool["name"] for tool in main_tools]
