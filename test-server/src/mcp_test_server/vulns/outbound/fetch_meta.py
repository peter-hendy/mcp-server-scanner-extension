from __future__ import annotations

from collections.abc import Iterable
from typing import Any

from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject, params_of
from mcp_test_server.vulns.outbound.observed_field_fetch import ObservedFieldFetch


class FetchMeta(ObservedFieldFetch):
    name = "fetch_meta"
    summary = "Any method (requests and notifications) fetches URL or host-shaped values under params._meta"

    def select(self, message: JsonObject, context: CallContext) -> Iterable[tuple[str, Any]]:
        params = params_of(message)
        if "_meta" in params:
            yield "params._meta", params["_meta"]
