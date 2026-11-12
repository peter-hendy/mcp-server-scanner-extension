from __future__ import annotations

from collections.abc import Iterable
from typing import Any

from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.vulns.outbound.observed_field_fetch import ObservedFieldFetch

_IGNORED_HEADERS = frozenset({"host", "origin", "content-length", "connection"})


class FetchHeaders(ObservedFieldFetch):
    name = "fetch_headers"
    summary = "MCP requests fetch URL or host-shaped values from any request header except Host and Origin"

    def select(self, message: JsonObject, context: CallContext) -> Iterable[tuple[str, Any]]:
        for name, value in context.headers.items():
            if name not in _IGNORED_HEADERS:
                yield f"header.{name}", value
