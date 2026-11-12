from __future__ import annotations

from collections.abc import Callable
from pathlib import Path

import httpx
import pytest

from tests.conftest import ServerFactory
from tests.support import McpSession, TestServer, live_server

CANARY = "http://canary.oast.example/probe"
Probe = Callable[[TestServer], None]


def _session(server: TestServer) -> McpSession:
    return server.session()


def _initialize_client_info(server: TestServer) -> None:
    session = server.session(initialize=False)
    session.initialize(clientInfo={"name": "x", "version": "1", "websiteUrl": CANARY,
                                   "icons": [{"src": "canary.oast.example"}]})


def _meta_on_notification(server: TestServer) -> None:
    _session(server).notify("notifications/progress", {"progressToken": "t", "progress": 1,
                                                       "_meta": {"trace": {"callback": CANARY}}})


def _meta_on_tool_call(server: TestServer) -> None:
    _session(server).request("tools/call", {"name": "echo", "arguments": {"message": "x"},
                                            "_meta": {"io.modelcontextprotocol/clientInfo": {"websiteUrl": CANARY}}})


def _cursor(server: TestServer) -> None:
    _session(server).request("prompts/list", {"cursor": CANARY})


def _resource_uri(server: TestServer) -> None:
    _session(server).request("resources/read", {"uri": "canary.oast.example"})


def _header(server: TestServer) -> None:
    _session(server).request("ping", None, referer=CANARY)


def _tool_nested_and_numeric(server: TestServer) -> None:
    _session(server).call_tool("fetch_document", {"url": "notes", "options": {"timeout_seconds": CANARY,
                                                                              "tags": ["x", CANARY]}})


def _dcr_metadata(server: TestServer) -> None:
    server.client.post("/register", json={"redirect_uris": ["https://client.example/cb"], "client_uri": CANARY,
                                          "logo_uri": CANARY, "jwks_uri": CANARY})


PROBES: dict[str, tuple[Probe, set[str]]] = {
    "fetch_client_info": (_initialize_client_info, {"params.clientInfo.websiteUrl", "params.clientInfo.icons[0].src"}),
    "fetch_meta": (_meta_on_notification, {"params._meta.trace.callback"}),
    "fetch_list_cursor": (_cursor, {"params.cursor"}),
    "fetch_resource_uri": (_resource_uri, {"params.uri"}),
    "fetch_headers": (_header, {"header.referer"}),
    "fetch_tool_arguments": (_tool_nested_and_numeric,
                             {"params.arguments.options.timeout_seconds", "params.arguments.options.tags[1]"}),
    "fetch_oauth_client_metadata": (_dcr_metadata, {"client_uri", "logo_uri", "jwks_uri"}),
}


def _paths(server: TestServer, source: str) -> set[str]:
    return {attempt["path"] for attempt in server.fetches() if attempt["source"] == source}


@pytest.mark.parametrize("vuln", PROBES)
def test_field_fetch_only_when_vulnerable(make_server: ServerFactory, vuln: str) -> None:
    probe, expected_paths = PROBES[vuln]
    vulnerable = make_server(default_mode="safe", vulns={vuln: "vulnerable", "unauthenticated_execution": "vulnerable"})
    probe(vulnerable)
    assert _paths(vulnerable, vuln) == expected_paths
    safe = make_server(default_mode="safe", vulns={"unauthenticated_execution": "vulnerable"})
    probe(safe)
    assert safe.fetches() == []


def test_meta_fetch_on_requests_too(make_server: ServerFactory) -> None:
    server = make_server(default_mode="safe", vulns={"fetch_meta": "vulnerable",
                                                     "unauthenticated_execution": "vulnerable"})
    _meta_on_tool_call(server)
    assert _paths(server, "fetch_meta") == {"params._meta.io.modelcontextprotocol/clientInfo.websiteUrl"}


def test_bare_host_is_fetched_over_http(server: TestServer) -> None:
    _resource_uri(server)
    server.fetches()
    assert "http://canary.oast.example" in server.getter.urls


def test_benign_traffic_triggers_no_fetch(server: TestServer) -> None:
    session = server.session()
    session.result("tools/list")
    session.call_tool("echo", {"message": "hello world"})
    session.call_tool("add", {"a": 1, "b": 2})
    assert server.fetches() == []


def test_fetch_tool_returns_fetched_body(server: TestServer) -> None:
    result = server.session().call_tool("fetch_document", {"url": CANARY})
    assert result["content"][0]["text"] == f"fetched {CANARY}"


def test_real_http_fetch_reaches_listener(tmp_path: Path) -> None:
    with live_server(tmp_path / "target") as target, live_server(tmp_path / "server") as base:
        canary = f"{target}/downstream/api/v1/token"
        with httpx.Client(base_url=base, timeout=10) as client:
            session = McpSession(client)  # type: ignore[arg-type]
            session.initialize()
            text = session.call_tool("fetch_document", {"url": canary})["content"][0]["text"]
        assert "downstream-protected-marker-4931" in text


def test_invalid_url_does_not_break_fetch_inspection(server: TestServer) -> None:
    server.session().request("prompts/list", {"cursor": "http://[::1/"})
    assert [attempt["url"] for attempt in server.fetches()] == ["http://[::1/"]
