from __future__ import annotations

from mcp_test_server.identity.access import Access
from mcp_test_server.identity.users import User
from mcp_test_server.protocol.catalog import Resource, resource_contents
from mcp_test_server.protocol.jsonrpc import RESOURCE_NOT_FOUND, JsonObject, JsonRpcError
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability
from mcp_test_server.vulns.identity.owned_records import PRIVATE_NOTES


class CrossIdentityResource(Vulnerability):
    name = "cross_identity_resource"
    summary = "resources/read returns another user's private notes; only resources/list is filtered by owner"

    def contribute(self, surface: ServerSurface) -> None:
        access = Access(surface.environment.users)
        for uri, note in PRIVATE_NOTES.items():
            surface.catalog.add_resource(Resource(uri, f"{note.owner}_private_notes", f"{note.owner}'s private notes",
                                                  access.resource(self._read), mime_type="text/markdown",
                                                  visible_to=access.owner(note.owner)))

    async def _read(self, uri: str, user: User) -> JsonObject:
        note = PRIVATE_NOTES[uri]
        if not (self.vulnerable or note.owned_by(user)):
            raise JsonRpcError(RESOURCE_NOT_FOUND, "Resource not found", {"uri": uri})
        return resource_contents(uri, note.text, "text/markdown")
