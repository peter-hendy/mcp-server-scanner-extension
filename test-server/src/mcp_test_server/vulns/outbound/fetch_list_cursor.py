from __future__ import annotations

from collections.abc import Iterable
from typing import Any

from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject, params_of
from mcp_test_server.vulns.outbound.observed_field_fetch import ObservedFieldFetch


class FetchListCursor(ObservedFieldFetch):
    name = "fetch_list_cursor"
    summary = "Every */list method fetches a URL or host-shaped cursor"

    def select(self, message: JsonObject, context: CallContext) -> Iterable[tuple[str, Any]]:
        params = params_of(message)
        if str(message.get("method", "")).endswith("/list") and "cursor" in params:
            yield "params.cursor", params["cursor"]
