from __future__ import annotations

from abc import abstractmethod
from collections.abc import Iterable
from typing import Any

from mcp_test_server.outbound.observer import UrlLeafFetchObserver
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability


class ObservedFieldFetch(Vulnerability):
    def contribute(self, surface: ServerSurface) -> None:
        observer = UrlLeafFetchObserver(self.name, self.select, self, surface.environment.fetcher)
        surface.observers.append(observer)

    @abstractmethod
    def select(self, message: JsonObject, context: CallContext) -> Iterable[tuple[str, Any]]: ...
