from __future__ import annotations

import pytest

from tests.conftest import ServerFactory
from tests.support import TestServer, prompt_text, resource_text, tool_text

LIST_METHODS = {
    "tools/list": "tools",
    "resources/list": "resources",
    "resources/templates/list": "resourceTemplates",
    "prompts/list": "prompts",
}
NOTIFICATIONS = (
    "notifications/initialized",
    "notifications/cancelled",
    "notifications/progress",
    "notifications/roots/list_changed",
)


def test_initialize_negotiates_version_and_advertises_capabilities(server: TestServer) -> None:
    session = server.session(initialize=False)
    response = session.initialize()
    result = response.json()["result"]
    assert result["protocolVersion"] == "2025-06-18"
    assert set(result["capabilities"]) == {"tools", "resources", "prompts", "completions", "logging"}
    assert result["capabilities"]["resources"]["subscribe"] is True
    assert response.headers["mcp-session-id"]


def test_initialize_falls_back_to_latest_handshake_version(server: TestServer) -> None:
    session = server.session(initialize=False)
    result = session.result("initialize", {"protocolVersion": "1999-01-01", "capabilities": {},
                                           "clientInfo": {"name": "x", "version": "1"}})
    assert result["protocolVersion"] == "2025-11-25"


@pytest.mark.parametrize(("method", "key"), LIST_METHODS.items())
def test_every_list_paginates_with_cursor(server: TestServer, method: str, key: str) -> None:
    session = server.session()
    seen: list[dict[str, object]] = []
    params: dict[str, object] = {}
    while True:
        result = session.result(method, params)
        assert len(result[key]) <= 3
        seen += result[key]
        if "nextCursor" not in result:
            break
        params = {"cursor": result["nextCursor"]}
    assert len(seen) > 3
    assert len({str(entry) for entry in seen}) == len(seen)


@pytest.mark.parametrize("method", LIST_METHODS)
def test_invalid_cursor_is_rejected(server: TestServer, method: str) -> None:
    assert server.session().request(method, {"cursor": "not-a-cursor"})["error"]["code"] == -32602


def test_tools_call_resources_read_template_read_and_prompts_get(server: TestServer) -> None:
    session = server.session()
    assert tool_text(session.call_tool("echo", {"message": "hi"})) == "hi"
    assert resource_text(session.result("resources/read", {"uri": "docs://readme"})) == "Benign readme"
    assert resource_text(session.result("resources/read", {"uri": "users://1/profile"})) == "Alice Example"
    result = session.result("prompts/get", {"name": "summarize", "arguments": {"text": "abc"}})
    assert "abc" in prompt_text(result)


def test_unknown_resource_returns_not_found(server: TestServer) -> None:
    assert server.session().request("resources/read", {"uri": "nope://x"})["error"]["code"] == -32002


@pytest.mark.parametrize("notification", NOTIFICATIONS)
def test_notifications_are_accepted_and_logged(server: TestServer, notification: str) -> None:
    response = server.session().notify(notification, {"progressToken": "t", "progress": 1, "requestId": 1})
    assert response.status_code == 202
    assert server.requests()[-1]["messages"][0] == {
        "method": notification,
        "id": None,
        "notification": True,
        "params": {"progressToken": "t", "progress": 1, "requestId": 1},
        "param_paths": ["params.progressToken", "params.progress", "params.requestId"],
    }


def test_completion_for_prompt_and_template(server: TestServer) -> None:
    session = server.session()
    prompt = session.result("completion/complete", {"ref": {"type": "ref/prompt", "name": "code_review"},
                                                    "argument": {"name": "language", "value": "ja"}})
    assert prompt["completion"]["values"] == ["java", "javascript"]
    template = session.result("completion/complete",
                              {"ref": {"type": "ref/resource", "uri": "users://{user_id}/profile"},
                               "argument": {"name": "user_id", "value": ""}})
    assert template["completion"]["values"] == ["1", "2"]


def test_logging_ping_subscribe_and_discover(server: TestServer) -> None:
    session = server.session()
    assert session.result("logging/setLevel", {"level": "debug"}) == {}
    assert session.request("logging/setLevel", {"level": "loud"})["error"]["code"] == -32602
    assert session.result("ping") == {}
    assert session.result("resources/subscribe", {"uri": "docs://readme"}) == {}
    assert session.result("resources/unsubscribe", {"uri": "docs://readme"}) == {}
    discover = server.session(initialize=False).result("server/discover")
    assert "2026-07-28" in discover["supportedVersions"]
    assert discover["serverInfo"]["name"] == "mcp-scanner-test-server"


@pytest.mark.parametrize(
    ("family", "method", "params"),
    [
        ("tools_call", "tools/call", {"name": "echo", "arguments": {"message": "x"}}),
        ("resources_read", "resources/read", {"uri": "docs://readme"}),
        ("prompts_get", "prompts/get", {"name": "summarize", "arguments": {"text": "x"}}),
        ("tools_list", "tools/list", {}),
        ("resources_list", "resources/list", {}),
        ("resource_templates_list", "resources/templates/list", {}),
        ("prompts_list", "prompts/list", {}),
        ("completion", "completion/complete",
         {"ref": {"type": "ref/prompt", "name": "code_review"}, "argument": {"name": "language", "value": ""}}),
        ("logging", "logging/setLevel", {"level": "info"}),
        ("ping", "ping", {}),
        ("subscribe", "resources/subscribe", {"uri": "docs://readme"}),
        ("server_discover", "server/discover", {}),
    ],
)
def test_disabled_family_is_method_not_found(
    make_server: ServerFactory, family: str, method: str, params: dict[str, object]
) -> None:
    assert make_server().session().request(method, params).get("result") is not None
    disabled = make_server(disabled_families=[family]).session()
    assert disabled.request(method, params)["error"]["code"] == -32601


def test_disabled_template_reads_leave_static_reads_working(make_server: ServerFactory) -> None:
    session = make_server(disabled_families=["resource_templates_read"]).session()
    assert session.request("resources/read", {"uri": "users://1/profile"})["error"]["code"] == -32601
    assert resource_text(session.result("resources/read", {"uri": "docs://readme"})) == "Benign readme"


def test_disabled_initialize_rejects_handshake(make_server: ServerFactory) -> None:
    response = make_server(disabled_families=["initialize"]).session(initialize=False).initialize()
    assert response.json()["error"]["code"] == -32601


def test_capabilities_follow_disabled_families(make_server: ServerFactory) -> None:
    server = make_server(disabled_families=["completion", "logging", "subscribe", "prompts_get", "prompts_list"])
    capabilities = server.session(initialize=False).initialize().json()["result"]["capabilities"]
    assert "completions" not in capabilities
    assert "logging" not in capabilities
    assert "prompts" not in capabilities
    assert capabilities["resources"]["subscribe"] is False


def test_batch_requests_return_batch_replies(server: TestServer) -> None:
    session = server.session()
    batch = [session.message("ping"), session.message("notifications/progress", {}, notification=True),
             session.message("tools/list")]
    replies = session.post(batch).json()
    assert [reply["id"] for reply in replies] == [batch[0]["id"], batch[2]["id"]]


def test_parse_error(server: TestServer) -> None:
    response = server.client.post("/mcp", content=b"{not json", headers=server.session().headers)
    assert response.status_code == 400
    assert response.json()["error"]["code"] == -32700
