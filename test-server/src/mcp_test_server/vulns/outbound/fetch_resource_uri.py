from __future__ import annotations

from collections.abc import Iterable
from typing import Any

from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject, params_of
from mcp_test_server.vulns.outbound.observed_field_fetch import ObservedFieldFetch


class FetchResourceUri(ObservedFieldFetch):
    name = "fetch_resource_uri"
    summary = "resources/read fetches URL or host-shaped uri values"

    def select(self, message: JsonObject, context: CallContext) -> Iterable[tuple[str, Any]]:
        if message.get("method") == "resources/read":
            yield "params.uri", params_of(message).get("uri")
