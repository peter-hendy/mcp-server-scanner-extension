from __future__ import annotations

from mcp_test_server.outbound.fetcher import OutboundFetcher, fetchable_url
from mcp_test_server.protocol.catalog import Arguments, Tool, text_content
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.json_leaves import iter_leaves
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability

FETCH_SCHEMA: JsonObject = {
    "type": "object",
    "properties": {
        "url": {"type": "string", "description": "Document URL"},
        "options": {
            "type": "object",
            "properties": {
                "referer": {"type": "string"},
                "timeout_seconds": {"type": "integer", "default": 5},
                "tags": {"type": "array", "items": {"type": "string"}},
            },
        },
    },
    "required": ["url"],
}


class FetchToolArguments(Vulnerability):
    name = "fetch_tool_arguments"
    summary = "fetch_document fetches every URL or host-shaped argument value, nested or numeric-typed"

    def contribute(self, surface: ServerSurface) -> None:
        self._fetcher: OutboundFetcher = surface.environment.fetcher
        surface.catalog.add_tool(Tool("fetch_document", "Fetch a document and return its text", FETCH_SCHEMA,
                                      self._call))

    async def _call(self, arguments: Arguments, context: CallContext) -> JsonObject:
        if not self.vulnerable:
            return text_content("Error: outbound fetching is restricted to the allowlist", is_error=True)
        bodies = [
            await self._fetcher.fetch(self.name, path, url)
            for path, value in iter_leaves(arguments, "params.arguments")
            if (url := fetchable_url(value)) is not None
        ]
        return text_content(bodies[0] if bodies else "No fetchable URL supplied")
