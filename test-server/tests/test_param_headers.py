from __future__ import annotations

from typing import Any

import pytest

from mcp_test_server.transport.param_headers import param_header_bindings
from tests.conftest import ServerFactory
from tests.test_protocol_versions import (
    HEADER_MISMATCH,
    MODERN_ONLY,
    modern_body,
    post,
    routing_headers,
    sentinel,
)

TOOL = "tenant_lookup"


def lookup(arguments: dict[str, Any]) -> dict[str, Any]:
    return modern_body("tools/call", {"name": TOOL, "arguments": arguments})


def headers(**params: str) -> dict[str, str]:
    return {**routing_headers("tools/call", TOOL), **{f"mcp-param-{name}": value for name, value in params.items()}}


class TestBindings:
    def test_binds_primitive_properties_reached_through_properties_only(self) -> None:
        schema = {"type": "object", "properties": {
            "tenant": {"type": "string", "x-mcp-header": "Tenant"},
            "nested": {"type": "object", "properties": {"flag": {"type": "boolean", "x-mcp-header": "Flag"}}},
            "listed": {"type": "array", "items": {"type": "string", "x-mcp-header": "Item"}},
            "number": {"type": "number", "x-mcp-header": "Number"},
        }}
        assert param_header_bindings(schema) == {"mcp-param-tenant": ("tenant",), "mcp-param-flag": ("nested", "flag")}


class TestTenantLookup:
    def test_is_listed_with_its_header_annotations(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        tools = post(server, modern_body("tools/list"), routing_headers("tools/list")).json()["result"]["tools"]
        schema = next(tool for tool in tools if tool["name"] == TOOL)["inputSchema"]
        assert schema["properties"]["tenant"]["x-mcp-header"] == "Tenant"
        assert schema["properties"]["priority"]["x-mcp-header"] == "Priority"

    @pytest.mark.parametrize("tenant_header", ["acme", sentinel("acme")])
    def test_answers_when_param_headers_match_the_arguments(self, make_server: ServerFactory,
                                                            tenant_header: str) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        response = post(server, lookup({"tenant": "acme", "priority": 2}),
                        headers(tenant=tenant_header, priority="2"))
        assert response.status_code == 200, response.text
        assert "acme" in response.json()["result"]["content"][0]["text"]

    def test_encoded_non_ascii_value_matches(self, make_server: ServerFactory) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        response = post(server, lookup({"tenant": "café"}), headers(tenant=sentinel("café")))
        assert response.status_code == 200, response.text

    @pytest.mark.parametrize(
        ("arguments", "params"),
        [
            ({"tenant": "acme"}, {}),
            ({"tenant": "acme"}, {"tenant": "other"}),
            ({"tenant": "acme", "priority": 2}, {"tenant": "acme", "priority": "3"}),
            ({"tenant": "acme"}, {"tenant": "acme", "priority": "1"}),
        ],
    )
    def test_rejects_a_missing_or_mismatched_param_header(self, make_server: ServerFactory,
                                                          arguments: dict[str, Any], params: dict[str, str]) -> None:
        server = make_server(protocol_versions=MODERN_ONLY)
        response = post(server, lookup(arguments), headers(**params))
        assert response.status_code == 400
        assert response.json()["error"]["code"] == HEADER_MISMATCH

    def test_handshake_calls_need_no_param_headers(self, make_server: ServerFactory) -> None:
        server = make_server(sessions="off")
        body = {"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                "params": {"name": TOOL, "arguments": {"tenant": "acme"}}}
        response = post(server, body, {"accept": "application/json, text/event-stream"})
        assert response.status_code == 200, response.text
