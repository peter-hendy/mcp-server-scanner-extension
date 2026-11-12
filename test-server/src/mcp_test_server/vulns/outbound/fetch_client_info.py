from __future__ import annotations

from collections.abc import Iterable
from typing import Any

from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject, params_of
from mcp_test_server.vulns.outbound.observed_field_fetch import ObservedFieldFetch


class FetchClientInfo(ObservedFieldFetch):
    name = "fetch_client_info"
    summary = "initialize fetches URL or host-shaped values anywhere in params (clientInfo, capabilities)"

    def select(self, message: JsonObject, context: CallContext) -> Iterable[tuple[str, Any]]:
        if message.get("method") == "initialize":
            params = params_of(message)
            yield from ((f"params.{key}", value) for key, value in params.items() if key != "_meta")
