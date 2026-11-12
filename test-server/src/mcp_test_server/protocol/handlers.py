from __future__ import annotations

from typing import Protocol

from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.families import Family
from mcp_test_server.protocol.jsonrpc import JsonObject


class MethodHandler(Protocol):
    @property
    def method(self) -> str: ...

    @property
    def family(self) -> Family: ...

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject | None: ...


class RequestObserver(Protocol):
    def observe(self, message: JsonObject, context: CallContext) -> None: ...
