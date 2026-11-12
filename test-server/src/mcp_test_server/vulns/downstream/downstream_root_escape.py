from __future__ import annotations

import posixpath
from abc import abstractmethod
from typing import ClassVar
from urllib.parse import quote

from mcp_test_server.protocol.catalog import Arguments, Tool, text_content
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability
from mcp_test_server.vulns.downstream.billing_api import BillingApi

DOT_SEGMENTS = {".", ".."}


class BillingRouteEscape(Vulnerability):
    tool: ClassVar[str]
    argument: ClassVar[str]
    collection: ClassVar[str]
    default: ClassVar[str]

    def contribute(self, surface: ServerSurface) -> None:
        self._api = BillingApi()
        schema = {
            "type": "object",
            "properties": {self.argument: {"type": "string", "default": self.default}},
            "required": [self.argument],
        }
        surface.catalog.add_tool(Tool(self.tool, f"Look up one of the billing service's {self.collection}", schema,
                                      self._call))

    def collection_path(self) -> str:
        return f"/api/v2/{self.collection}/"

    @abstractmethod
    def downstream_path(self, value: str) -> str | None: ...

    async def _call(self, arguments: Arguments, context: CallContext) -> JsonObject:
        path = self.downstream_path(str(arguments.get(self.argument, "")))
        if path is None:
            return text_content(f"invalid {self.argument}", is_error=True)
        return text_content(self._api.get(path).body)


class DownstreamRawSegment(BillingRouteEscape):
    name = "downstream_raw_segment"
    summary = "get_invoice joins invoice_id into the billing API path raw, so dot segments reach the API root"
    tool = "get_invoice"
    argument = "invoice_id"
    collection = "invoices"
    default = "INV-1001"

    def downstream_path(self, value: str) -> str | None:
        if self.vulnerable:
            route, _, query = (self.collection_path() + value).partition("?")
            return posixpath.normpath(route) + ("?" + query if query else "")
        return self.collection_path() + quote(value, safe="")


class DownstreamEncodedDotSegment(BillingRouteEscape):
    name = "downstream_encoded_dot_segment"
    summary = "get_order percent-encodes order_id but leaves a bare '..' segment for URL resolution to apply"
    tool = "get_order"
    argument = "order_id"
    collection = "orders"
    default = "ORD-2001"

    def downstream_path(self, value: str) -> str | None:
        if value in DOT_SEGMENTS and not self.vulnerable:
            return None
        return posixpath.normpath(self.collection_path() + quote(value, safe=""))
