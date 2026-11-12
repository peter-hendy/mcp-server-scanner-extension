from __future__ import annotations

from mcp_test_server.identity.access import Access
from mcp_test_server.identity.users import User
from mcp_test_server.protocol.catalog import Arguments, Resource, Tool, resource_contents, text_content
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface

HANDBOOK_URI = "notes://team/handbook.md"
HANDBOOK = "# Team handbook\n\nStand-up is at 09:30. Deploys freeze on Fridays.\n"
SEARCH_RESULTS = "notes://team/handbook.md: Team handbook"
QUERY_SCHEMA = {"type": "object", "properties": {"query": {"type": "string"}}, "required": ["query"]}


class SharedIdentityControls:
    def contribute(self, surface: ServerSurface) -> None:
        access = Access(surface.environment.users)
        surface.catalog.add_tool(Tool("search_docs", "Search the shared team documents", QUERY_SCHEMA,
                                      access.tool(_search_docs), visible_to=access.any_user()))
        surface.catalog.add_resource(Resource(HANDBOOK_URI, "team_handbook", "Handbook shared with every user",
                                              access.resource(_read_handbook), mime_type="text/markdown",
                                              visible_to=access.any_user()))


async def _search_docs(arguments: Arguments, user: User, context: CallContext) -> JsonObject:
    return text_content(SEARCH_RESULTS)


async def _read_handbook(uri: str, user: User) -> JsonObject:
    return resource_contents(uri, HANDBOOK, "text/markdown")
