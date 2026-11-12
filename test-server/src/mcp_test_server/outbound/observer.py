from __future__ import annotations

from collections.abc import Callable, Iterable
from typing import Any

from mcp_test_server.outbound.fetcher import OutboundFetcher, fetchable_url
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.json_leaves import iter_leaves
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.weakness import Switchable

FieldSelector = Callable[[JsonObject, CallContext], Iterable[tuple[str, Any]]]


class UrlLeafFetchObserver:
    def __init__(self, source: str, selector: FieldSelector, switch: Switchable, fetcher: OutboundFetcher) -> None:
        self._source = source
        self._selector = selector
        self._switch = switch
        self._fetcher = fetcher

    def observe(self, message: JsonObject, context: CallContext) -> None:
        if not self._switch.vulnerable:
            return
        for root_path, root in self._selector(message, context):
            for path, value in iter_leaves(root, root_path):
                url = fetchable_url(value)
                if url is not None:
                    self._fetcher.fetch_in_background(self._source, path, url)
