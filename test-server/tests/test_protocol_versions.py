from __future__ import annotations

import base64
import json
from pathlib import Path
from typing import Any

import httpx
import pytest

from mcp_test_server.cli import build_parser, cli_layer
from mcp_test_server.settings import ServerSettings, load_settings, settings_from_environment
from tests.conftest import ServerFactory
from tests.support import TestServer, live_server

MODERN = "2026-07-28"
ALL_VERSIONS = ["2026-07-28", "2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05"]
ONLY_OLDEST = ["2024-11-05"]
MIDDLE_PAIR = ["2025-06-18", "2025-03-26"]
MODERN_ONLY = ["2026-07-28"]
HEADER_MISMATCH = -32020
INVALID_PARAMS = -32602


def envelope(version: str = MODERN, capabilities: Any = None) -> dict[str, Any]:
    return {
        "io.modelcontextprotocol/protocolVersion": version,
        "io.modelcontextprotocol/clientCapabilities": {} if capabilities is None else capabilities,
        "io.modelcontextprotocol/clientInfo": {"name": "pytest", "version": "1"},
    }


def modern_body(method: str, params: dict[str, Any] | None = None, **meta: Any) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": 1, "method": method,
            "params": {**(params or {}), "_meta": envelope(**meta)}}


def routing_headers(method: str, name: str | None = None, version: str = MODERN) -> dict[str, str]:
    headers = {"accept": "application/json, text/event-stream", "mcp-protocol-version": version,
               "mcp-method": method}
    if name is not None:
        headers["mcp-name"] = name
    return headers


def post(server: TestServer, body: Any, headers: dict[str, str]) -> httpx.Response:
    return server.client.post("/mcp", json=body, headers=headers)


def initialize(server: TestServer, version: str) -> httpx.Response:
    body = {"jsonrpc": "2.0", "id": 1, "method": "initialize",
            "params": {"protocolVersion": version, "capabilities": {}, "clientInfo": {"name": "x", "version": "1"}}}
    return post(server, body, {"accept": "application/json, text/event-stream"})


def sentinel(value: str) -> str:
    return f"=?base64?{base64.b64encode(value.encode()).decode()}?="


class TestSettings:
    def test_default_enables_every_known_version_newest_first(self) -> None:
        assert ServerSettings().protocol_versions == tuple(ALL_VERSIONS)

    def test_cli_flag_is_repeatable(self) -> None:
        arguments = build_parser().parse_args(["--protocol-version", "2025-06-18", "--protocol-version", MODERN])
        assert ServerSettings.from_mapping(cli_layer(arguments)).protocol_versions == ("2025-06-18", MODERN)

    def test_cli_layer_omits_unset_flag(self) -> None:
        assert "protocol_versions" not in cli_layer(build_parser().parse_args([]))

    def test_environment_layer(self) -> None:
        raw = settings_from_environment({"MCP_TEST_PROTOCOL_VERSIONS": "2025-06-18, 2025-03-26"})
        assert ServerSettings.from_mapping(raw).protocol_versions == tuple(MIDDLE_PAIR)

    def test_config_file_then_environment_then_cli(self, tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
        config = tmp_path / "config.json"
        config.write_text(json.dumps({"protocol_versions": ONLY_OLDEST}))
        assert load_settings({}, config).protocol_versions == tuple(ONLY_OLDEST)
        monkeypatch.setenv("MCP_TEST_PROTOCOL_VERSIONS", MODERN)
        assert load_settings({}, config).protocol_versions == (MODERN,)
        assert load_settings({"protocol_versions": MIDDLE_PAIR}, config).protocol_versions == tuple(MIDDLE_PAIR)

    @pytest.mark.parametrize("value", [["1999-01-01"], [MODERN, "2025-13-01"], []])
    def test_unknown_or_empty_versions_are_rejected(self, value: list[str]) -> None:
        with pytest.raises(ValueError, match="protocol version"):
            ServerSettings.from_mapping({"protocol_versions": value})

    def test_effective_versions_are_visible_in_settings_view(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=MIDDLE_PAIR)
        assert server.client.get("/_test/settings").json()["protocol_versions"] == MIDDLE_PAIR


class TestInitialize:
    @pytest.mark.parametrize(
        ("enabled", "requested", "answered"),
        [
            (ALL_VERSIONS, "2025-03-26", "2025-03-26"),
            (ALL_VERSIONS, MODERN, "2025-11-25"),
            (ALL_VERSIONS, "1999-01-01", "2025-11-25"),
            (ONLY_OLDEST, "2025-06-18", "2024-11-05"),
            (ONLY_OLDEST, "2024-11-05", "2024-11-05"),
            (MIDDLE_PAIR, "2025-03-26", "2025-03-26"),
            (MIDDLE_PAIR, "2025-11-25", "2025-06-18"),
            (MIDDLE_PAIR + MODERN_ONLY, MODERN, "2025-06-18"),
        ],
    )
    def test_negotiates_only_enabled_handshake_versions(
        self, make_server: ServerFactory, enabled: list[str], requested: str, answered: str
    ) -> None:
        response = initialize(make_server(protocol_versions=enabled), requested)
        assert response.json()["result"]["protocolVersion"] == answered
        assert response.headers["mcp-session-id"]

    def test_modern_only_server_refuses_initialize(self, make_server: ServerFactory) -> None:
        response = initialize(make_server(protocol_versions=MODERN_ONLY), "2025-06-18")
        error = response.json()["error"]
        assert error["code"] == INVALID_PARAMS
        assert "unsupported protocol version" in error["message"].lower()
        assert error["data"] == {"supported": MODERN_ONLY}
        assert "mcp-session-id" not in response.headers


class TestServerDiscover:
    @pytest.mark.parametrize("enabled", [ALL_VERSIONS, MODERN_ONLY, [MODERN, "2024-11-05"]])
    def test_lists_enabled_modern_versions(self, make_server: ServerFactory, enabled: list[str]) -> None:
        server = make_server(protocol_versions=enabled)
        response = post(server, modern_body("server/discover"), routing_headers("server/discover"))
        result = response.json()["result"]
        assert result["supportedVersions"] == [MODERN]
        assert {"ttlMs": 0, "cacheScope": "private", "resultType": "complete"}.items() <= result.items()
        assert result["serverInfo"]["name"] == "mcp-scanner-test-server"
        assert set(result["capabilities"]) >= {"tools", "resources", "prompts"}
        assert result["instructions"]

    @pytest.mark.parametrize("enabled", [ONLY_OLDEST, MIDDLE_PAIR])
    def test_is_method_not_found_without_a_modern_version(self, make_server: ServerFactory,
                                                           enabled: list[str]) -> None:
        session = make_server(protocol_versions=enabled).session()
        assert session.request("server/discover")["error"]["code"] == -32601


class TestProtocolVersionHeader:
    @pytest.mark.parametrize(("enabled", "header"), [(ONLY_OLDEST, "2025-06-18"), (MIDDLE_PAIR, "2024-11-05"),
                                                     (MIDDLE_PAIR, MODERN), (ALL_VERSIONS, "1999-01-01")])
    def test_header_outside_enabled_set_is_400(self, make_server: ServerFactory, enabled: list[str],
                                               header: str) -> None:
        session = make_server(protocol_versions=enabled).session()
        assert session.post(session.message("ping"), {"mcp-protocol-version": header}).status_code == 400

    def test_initialize_negotiates_despite_an_unsupported_header(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=ONLY_OLDEST)
        body = {"jsonrpc": "2.0", "id": 1, "method": "initialize",
                "params": {"protocolVersion": "2025-06-18", "capabilities": {},
                           "clientInfo": {"name": "x", "version": "1"}}}
        response = post(server, body, {"accept": "application/json, text/event-stream",
                                       "mcp-protocol-version": "2025-06-18"})
        assert response.json()["result"]["protocolVersion"] == "2024-11-05"

    @pytest.mark.parametrize(("enabled", "header"), [(ONLY_OLDEST, "2024-11-05"), (MIDDLE_PAIR, "2025-03-26")])
    def test_enabled_handshake_header_is_accepted(self, make_server: ServerFactory, enabled: list[str],
                                                  header: str) -> None:
        session = make_server(protocol_versions=enabled).session()
        assert session.result("ping", **{"mcp-protocol-version": header}) == {}


class TestModernRequests:
    @pytest.mark.parametrize("enabled", [ALL_VERSIONS, MODERN_ONLY])
    def test_valid_modern_tool_call_runs_without_a_session(self, make_server: ServerFactory,
                                                           enabled: list[str]) -> None:
        server = make_server(protocol_versions=enabled)
        body = modern_body("tools/call", {"name": "echo", "arguments": {"message": "stateless"}})
        response = post(server, body, routing_headers("tools/call", "echo"))
        assert response.status_code == 200, response.text
        assert response.json()["result"]["content"][0]["text"] == "stateless"
        assert "mcp-session-id" not in response.headers

    def test_unknown_session_id_is_ignored(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        headers = {**routing_headers("tools/list"), "mcp-session-id": "never-issued"}
        assert post(server, modern_body("tools/list"), headers).json()["result"]["tools"]

    def test_unenabled_modern_version_is_unsupported(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=MIDDLE_PAIR, sessions="off")
        response = post(server, modern_body("tools/list"), routing_headers("tools/list", version="2025-06-18"))
        error = response.json()["error"]
        assert response.status_code == 400
        assert error["code"] == INVALID_PARAMS
        assert "unsupported protocol version" in error["message"].lower()
        assert error["data"]["supported"] == []

    def test_handshake_version_in_envelope_is_unsupported(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=ALL_VERSIONS)
        body = modern_body("tools/list", version="2025-06-18")
        response = post(server, body, routing_headers("tools/list", version="2025-06-18"))
        assert response.status_code == 400
        assert response.json()["error"]["code"] == INVALID_PARAMS
        assert response.json()["error"]["data"]["supported"] == [MODERN]

    @pytest.mark.parametrize("capabilities", ["none", ["roots"], 7])
    def test_client_capabilities_must_be_an_object(self, make_server: ServerFactory, capabilities: Any) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        response = post(server, modern_body("tools/list", capabilities=capabilities), routing_headers("tools/list"))
        error = response.json()["error"]
        assert response.status_code == 400
        assert error["code"] == INVALID_PARAMS
        assert "io.modelcontextprotocol/clientCapabilities" in error["message"]

    def test_missing_client_capabilities_is_invalid_params(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        body = modern_body("tools/list")
        del body["params"]["_meta"]["io.modelcontextprotocol/clientCapabilities"]
        error = post(server, body, routing_headers("tools/list")).json()["error"]
        assert error["code"] == INVALID_PARAMS
        assert "io.modelcontextprotocol/clientCapabilities" in error["message"]

    @pytest.mark.parametrize("enabled", [ALL_VERSIONS, MODERN_ONLY])
    def test_protocol_version_header_must_match_envelope(self, make_server: ServerFactory,
                                                         enabled: list[str]) -> None:
        server = make_server(protocol_versions=enabled)
        headers = routing_headers("tools/list")
        del headers["mcp-protocol-version"]
        response = post(server, modern_body("tools/list"), headers)
        assert response.status_code == 400
        assert response.json()["error"]["code"] == HEADER_MISMATCH
        assert response.json()["id"] == 1

    def test_enabled_but_different_header_version_is_mismatch(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=ALL_VERSIONS)
        response = post(server, modern_body("tools/list"), routing_headers("tools/list", version="2025-06-18"))
        assert response.status_code == 400
        assert response.json()["error"]["code"] == HEADER_MISMATCH

    @pytest.mark.parametrize("method_header", [None, "", "tools/call"])
    def test_method_header_is_required_and_must_match(self, make_server: ServerFactory,
                                                      method_header: str | None) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        headers = routing_headers("tools/list")
        if method_header is None:
            del headers["mcp-method"]
        else:
            headers["mcp-method"] = method_header
        response = post(server, modern_body("tools/list"), headers)
        assert response.status_code == 400
        assert response.json()["error"]["code"] == HEADER_MISMATCH

    @pytest.mark.parametrize(
        ("method", "params", "name_header"),
        [
            ("tools/call", {"name": "echo", "arguments": {"message": "x"}}, None),
            ("tools/call", {"name": "echo", "arguments": {"message": "x"}}, "add"),
            ("resources/read", {"uri": "docs://readme"}, None),
            ("resources/read", {"uri": "docs://readme"}, "docs://other"),
            ("prompts/get", {"name": "summarize", "arguments": {"text": "x"}}, sentinel("code_review")),
        ],
    )
    def test_name_header_is_required_and_must_match(self, make_server: ServerFactory, method: str,
                                                    params: dict[str, Any], name_header: str | None) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        response = post(server, modern_body(method, params), routing_headers(method, name_header))
        assert response.status_code == 400
        assert response.json()["error"]["code"] == HEADER_MISMATCH

    @pytest.mark.parametrize(
        ("method", "params", "name_header"),
        [
            ("resources/read", {"uri": "docs://readme"}, "docs://readme"),
            ("resources/read", {"uri": "docs://readme"}, sentinel("docs://readme")),
            ("prompts/get", {"name": "summarize", "arguments": {"text": "x"}}, sentinel("summarize")),
        ],
    )
    def test_name_header_matches_plain_or_base64_sentinel(self, make_server: ServerFactory, method: str,
                                                          params: dict[str, Any], name_header: str) -> None:
        server = make_server(protocol_versions=ALL_VERSIONS)
        response = post(server, modern_body(method, params), routing_headers(method, name_header))
        assert response.status_code == 200, response.text
        assert "result" in response.json()

    @pytest.mark.parametrize("enabled", [ALL_VERSIONS, MODERN_ONLY])
    def test_modern_header_without_envelope_is_rejected(self, make_server: ServerFactory,
                                                        enabled: list[str]) -> None:
        server = make_server(protocol_versions=enabled, sessions="off")
        body = {"jsonrpc": "2.0", "id": 3, "method": "tools/list", "params": {}}
        response = post(server, body, routing_headers("tools/list"))
        assert response.status_code == 400
        assert response.json()["error"]["code"] == INVALID_PARAMS
        assert "_meta" in response.json()["error"]["message"]

    def test_server_discover_and_notifications_need_no_envelope(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        discover = {"jsonrpc": "2.0", "id": 4, "method": "server/discover"}
        assert post(server, discover, routing_headers("server/discover")).json()["result"]["supportedVersions"]
        notification = {"jsonrpc": "2.0", "method": "notifications/cancelled", "params": {"requestId": 1}}
        assert post(server, notification, routing_headers("notifications/cancelled")).status_code == 202

    def test_handshake_requests_keep_the_session_policy(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=ALL_VERSIONS)
        body = {"jsonrpc": "2.0", "id": 5, "method": "tools/list", "params": {"_meta": {"progressToken": 1}}}
        assert post(server, body, {"accept": "application/json"}).status_code == 400


class TestRequestLog:
    def test_modern_requests_are_logged_with_headers_and_meta_paths(self, make_server: ServerFactory,
                                                                     tmp_path: Path) -> None:
        log_file = tmp_path / "requests.jsonl"
        server = make_server(protocol_versions=MODERN_ONLY, request_log=str(log_file))
        body = modern_body("tools/call", {"name": "echo", "arguments": {"message": "x"}})
        post(server, body, routing_headers("tools/call", "echo"))
        entry = json.loads(log_file.read_text().splitlines()[-1])
        assert ["mcp-method", "tools/call"] in entry["headers"]
        assert ["mcp-name", "echo"] in entry["headers"]
        assert entry["messages"][0]["param_paths"] == [
            "params.name",
            "params.arguments.message",
            "params._meta.io.modelcontextprotocol/protocolVersion",
            "params._meta.io.modelcontextprotocol/clientCapabilities",
            "params._meta.io.modelcontextprotocol/clientInfo.name",
            "params._meta.io.modelcontextprotocol/clientInfo.version",
        ]
        assert server.requests()[-1]["messages"] == entry["messages"]


class TestLegacySse:
    @pytest.mark.parametrize("enabled", [ONLY_OLDEST, MIDDLE_PAIR])
    def test_handshake_over_legacy_sse(self, tmp_path: Path, enabled: list[str]) -> None:
        with live_server(tmp_path, protocol_versions=enabled) as base, \
                httpx.Client(base_url=base, timeout=10) as client:
            with client.stream("GET", "/sse") as stream:
                lines = stream.iter_lines()
                assert next(lines) == "event: endpoint"
                endpoint = next(lines).removeprefix("data: ")
                body = {"jsonrpc": "2.0", "id": 9, "method": "initialize",
                        "params": {"protocolVersion": "2025-11-25", "capabilities": {},
                                   "clientInfo": {"name": "x", "version": "1"}}}
                assert client.post(endpoint, json=body).status_code == 202
                event_lines = [next(lines) for _ in range(3)]
            reply = json.loads(event_lines[2].removeprefix("data: "))
            assert reply["result"]["protocolVersion"] == enabled[0]
