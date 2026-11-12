from __future__ import annotations

import json
from pathlib import Path

import httpx

from tests.conftest import ServerFactory
from tests.support import TestServer, live_server


def test_required_session_rejects_missing_and_unknown_ids(server: TestServer) -> None:
    session = server.session(initialize=False)
    assert session.post(session.message("tools/list")).status_code == 400
    assert session.post(session.message("tools/list"), {"mcp-session-id": "nope"}).status_code == 404


def test_session_can_be_deleted(make_server: ServerFactory) -> None:
    server = make_server(vulns={"session_delete_ignored": "safe"})
    session = server.session()
    assert server.client.delete("/mcp", headers=session.headers).status_code == 200
    assert session.post(session.message("ping")).status_code == 404


def test_optional_and_off_session_policies(make_server: ServerFactory) -> None:
    optional = make_server(sessions="optional").session(initialize=False)
    assert optional.result("ping") == {}
    off = make_server(sessions="off").session(initialize=False)
    assert "mcp-session-id" not in off.initialize().headers
    assert off.result("ping", **{"mcp-session-id": "anything"}) == {}


def test_unsupported_protocol_version_header_rejected(server: TestServer) -> None:
    session = server.session()
    assert session.post(session.message("ping"), {"mcp-protocol-version": "1999-01-01"}).status_code == 400


def test_get_on_streamable_endpoint_is_405(server: TestServer) -> None:
    assert server.client.get("/mcp", headers={"accept": "text/event-stream"}).status_code == 405


def test_sse_response_mode(make_server: ServerFactory) -> None:
    session = make_server(response_mode="sse").session(initialize=False)
    response = session.post(session.message("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                                                           "clientInfo": {"name": "x", "version": "1"}}))
    assert response.headers["content-type"].startswith("text/event-stream")
    event = response.text.split("data: ", 1)[1].strip()
    assert json.loads(event)["result"]["serverInfo"]["name"] == "mcp-scanner-test-server"


def test_disabled_transports_are_not_mounted(make_server: ServerFactory) -> None:
    server = make_server(disabled_families=["streamable_http", "sse"])
    assert server.client.post("/mcp", json={}).status_code == 404
    assert server.client.post("/messages?session_id=x", json={}).status_code == 404


def test_legacy_sse_transport_round_trip(tmp_path: Path) -> None:
    with live_server(tmp_path) as base, httpx.Client(base_url=base, timeout=10) as client:
        with client.stream("GET", "/sse") as stream:
            lines = stream.iter_lines()
            assert next(lines) == "event: endpoint"
            endpoint = next(lines).removeprefix("data: ")
            body = {"jsonrpc": "2.0", "id": 7, "method": "tools/call",
                    "params": {"name": "echo", "arguments": {"message": "over-sse"}}}
            assert client.post(endpoint, json=body).status_code == 202
            event_lines = [next(lines) for _ in range(3)]
        assert event_lines[1] == "event: message"
        reply = json.loads(event_lines[2].removeprefix("data: "))
        assert reply["id"] == 7
        assert reply["result"]["content"][0]["text"] == "over-sse"


def test_unknown_sse_channel(server: TestServer) -> None:
    assert server.client.post("/messages?session_id=missing", json={}).status_code == 404


def test_empty_batch_is_invalid_request(server: TestServer) -> None:
    session = server.session()
    response = session.post([])
    assert response.json()["error"]["code"] == -32600


def test_disabled_initialize_opens_no_session(make_server: ServerFactory) -> None:
    response = make_server(disabled_families=["initialize"]).session(initialize=False).initialize()
    assert "mcp-session-id" not in response.headers
