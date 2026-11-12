from __future__ import annotations

from typing import Any

import pytest

from tests.conftest import ServerFactory
from tests.support import McpSession, TestServer, resource_text, tool_text

ALICE_MARKER = "ALICE-PRIVATE-7f3c"
BOB_MARKER = "BOB-PRIVATE-91ad"
IDENTITY_TOOLS = {"get_document", "delete_project", "list_users", "search_docs", "whoami"}
IDENTITY_RESOURCES = {"notes://alice/private.md", "notes://bob/private.md", "notes://team/handbook.md"}
MODES = ["vulnerable", "safe"]


def _server(make_server: ServerFactory, mode: str) -> TestServer:
    return make_server(default_mode="safe", vulns={
        "cross_identity_object": mode, "cross_identity_resource": mode,
        "cross_identity_role": mode, "cross_identity_session": mode,
    })


def _as(server: TestServer, user: str) -> McpSession:
    return server.session({"authorization": f"Bearer {user}-token"})


def _listed(session: McpSession, method: str, key: str, field: str) -> set[str]:
    names: set[str] = set()
    params: dict[str, Any] = {}
    while True:
        result = session.result(method, params)
        names |= {entry[field] for entry in result[key]}
        if "nextCursor" not in result:
            return names
        params = {"cursor": result["nextCursor"]}


def _tools(session: McpSession) -> set[str]:
    return _listed(session, "tools/list", "tools", "name") & IDENTITY_TOOLS


def _resources(session: McpSession) -> set[str]:
    return _listed(session, "resources/list", "resources", "uri") & IDENTITY_RESOURCES


def _read(session: McpSession, uri: str) -> dict[str, Any]:
    return session.request("resources/read", {"uri": uri})


@pytest.mark.parametrize("mode", MODES)
def test_anonymous_and_unknown_tokens_get_401_while_static_users_are_admitted(
    make_server: ServerFactory, mode: str
) -> None:
    server = _server(make_server, mode)
    assert server.session(initialize=False).initialize().status_code == 401
    assert server.session({"authorization": "Bearer mallory-token"}, initialize=False).initialize().status_code == 401
    for user in ("alice", "bob", "carol"):
        assert _as(server, user).headers["mcp-session-id"]


def test_anonymous_callers_admitted_by_the_gate_are_still_refused_by_identity_surfaces(
    make_server: ServerFactory,
) -> None:
    session = make_server().session()
    assert _tools(session) == set()
    assert _resources(session) == set()
    refused = session.call_tool("get_document", {"doc_id": "d-alice-0001"})
    assert refused["isError"] is True
    assert ALICE_MARKER not in tool_text(refused)
    assert "error" in _read(session, "notes://alice/private.md")


@pytest.mark.parametrize("mode", MODES)
def test_tool_listing_differs_per_user(make_server: ServerFactory, mode: str) -> None:
    server = _server(make_server, mode)
    common = {"get_document", "search_docs", "whoami"}
    assert _tools(_as(server, "alice")) == common | {"delete_project", "list_users"}
    assert _tools(_as(server, "bob")) == common | {"delete_project"}
    assert _tools(_as(server, "carol")) == common


@pytest.mark.parametrize("mode", MODES)
def test_resource_listing_shows_private_notes_only_to_their_owner(make_server: ServerFactory, mode: str) -> None:
    server = _server(make_server, mode)
    assert _resources(_as(server, "alice")) == {"notes://alice/private.md", "notes://team/handbook.md"}
    assert _resources(_as(server, "bob")) == {"notes://bob/private.md", "notes://team/handbook.md"}
    assert _resources(_as(server, "carol")) == {"notes://team/handbook.md"}


@pytest.mark.parametrize("mode", MODES)
def test_owners_read_their_own_documents(make_server: ServerFactory, mode: str) -> None:
    server = _server(make_server, mode)
    assert ALICE_MARKER in tool_text(_as(server, "alice").call_tool("get_document", {"doc_id": "d-alice-0001"}))
    assert BOB_MARKER in tool_text(_as(server, "bob").call_tool("get_document", {"doc_id": "d-bob-0042"}))


def test_any_user_reads_any_document_only_when_object_access_is_vulnerable(make_server: ServerFactory) -> None:
    vulnerable = _as(_server(make_server, "vulnerable"), "bob").call_tool("get_document", {"doc_id": "d-alice-0001"})
    assert vulnerable["isError"] is False
    assert ALICE_MARKER in tool_text(vulnerable)
    safe = _as(_server(make_server, "safe"), "bob").call_tool("get_document", {"doc_id": "d-alice-0001"})
    assert safe["isError"] is True
    assert ALICE_MARKER not in tool_text(safe)


@pytest.mark.parametrize("mode", MODES)
def test_unknown_document_is_an_error(make_server: ServerFactory, mode: str) -> None:
    result = _as(_server(make_server, mode), "alice").call_tool("get_document", {"doc_id": "d-nobody-9999"})
    assert result["isError"] is True


@pytest.mark.parametrize("mode", MODES)
def test_owners_read_their_own_private_notes(make_server: ServerFactory, mode: str) -> None:
    server = _server(make_server, mode)
    assert ALICE_MARKER in resource_text(_as(server, "alice").result("resources/read",
                                                                     {"uri": "notes://alice/private.md"}))
    assert BOB_MARKER in resource_text(_as(server, "bob").result("resources/read", {"uri": "notes://bob/private.md"}))


def test_any_user_reads_private_notes_only_when_resource_access_is_vulnerable(make_server: ServerFactory) -> None:
    vulnerable = _read(_as(_server(make_server, "vulnerable"), "carol"), "notes://alice/private.md")
    assert ALICE_MARKER in resource_text(vulnerable["result"])
    safe = _read(_as(_server(make_server, "safe"), "carol"), "notes://alice/private.md")
    assert "result" not in safe
    assert ALICE_MARKER not in str(safe)


@pytest.mark.parametrize("user", ["alice", "bob"])
@pytest.mark.parametrize("mode", MODES)
def test_listed_users_delete_projects(make_server: ServerFactory, user: str, mode: str) -> None:
    result = _as(_server(make_server, mode), user).call_tool("delete_project", {"project_id": "p-1"})
    assert tool_text(result) == "project p-1 scheduled for deletion"


def test_reader_deletes_projects_only_when_role_checks_are_vulnerable(make_server: ServerFactory) -> None:
    alice = _as(_server(make_server, "vulnerable"), "alice").call_tool("delete_project", {"project_id": "p-1"})
    vulnerable = _as(_server(make_server, "vulnerable"), "carol").call_tool("delete_project", {"project_id": "p-1"})
    assert vulnerable == alice
    safe = _as(_server(make_server, "safe"), "carol").call_tool("delete_project", {"project_id": "p-1"})
    assert safe["isError"] is True
    assert "scheduled" not in tool_text(safe)


def test_delete_project_replays_are_stable(make_server: ServerFactory) -> None:
    alice = _as(_server(make_server, "vulnerable"), "alice")
    assert alice.call_tool("delete_project", {"project_id": "p-1"}) == alice.call_tool("delete_project",
                                                                                       {"project_id": "p-1"})


@pytest.mark.parametrize("user", ["bob", "carol"])
def test_non_admins_list_users_only_when_role_checks_are_vulnerable(make_server: ServerFactory, user: str) -> None:
    admin = _as(_server(make_server, "safe"), "alice").call_tool("list_users", {})
    assert admin["isError"] is False
    assert "carol" in tool_text(admin)
    assert _as(_server(make_server, "vulnerable"), user).call_tool("list_users", {}) == admin
    assert _as(_server(make_server, "safe"), user).call_tool("list_users", {})["isError"] is True


def _whoami_on(server: TestServer, session_owner: str, caller: str) -> Any:
    owner_session = _as(server, session_owner).headers["mcp-session-id"]
    hijacker = server.session({"authorization": f"Bearer {caller}-token", "mcp-session-id": owner_session},
                              initialize=False)
    return hijacker.post(hijacker.message("tools/call", {"name": "whoami", "arguments": {}}))


@pytest.mark.parametrize("mode", MODES)
def test_whoami_names_the_session_owner(make_server: ServerFactory, mode: str) -> None:
    assert tool_text(_as(_server(make_server, mode), "bob").call_tool("whoami", {})) == "bob"


def test_foreign_session_runs_in_its_owners_context_only_when_session_binding_is_vulnerable(
    make_server: ServerFactory,
) -> None:
    vulnerable = _whoami_on(_server(make_server, "vulnerable"), "alice", "bob")
    assert vulnerable.status_code == 200
    assert tool_text(vulnerable.json()["result"]) == "alice"
    assert _whoami_on(_server(make_server, "safe"), "alice", "bob").status_code == 404


def test_session_owner_keeps_using_their_session_when_binding_is_safe(make_server: ServerFactory) -> None:
    response = _whoami_on(_server(make_server, "safe"), "alice", "alice")
    assert tool_text(response.json()["result"]) == "alice"


@pytest.mark.parametrize("mode", MODES)
def test_shared_controls_answer_everyone_identically(make_server: ServerFactory, mode: str) -> None:
    server = _server(make_server, mode)
    sessions = [_as(server, user) for user in ("alice", "bob", "carol")]
    searches = [session.call_tool("search_docs", {"query": "handbook"}) for session in sessions]
    handbooks = [session.result("resources/read", {"uri": "notes://team/handbook.md"}) for session in sessions]
    assert searches[0]["isError"] is False
    assert all(search == searches[0] for search in searches)
    assert all(handbook == handbooks[0] for handbook in handbooks)
    assert ALICE_MARKER not in str(searches) + str(handbooks)
