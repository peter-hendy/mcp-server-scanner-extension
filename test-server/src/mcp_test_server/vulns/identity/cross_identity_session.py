from __future__ import annotations

from typing import Any

from mcp_test_server.identity.access import Access
from mcp_test_server.identity.users import User
from mcp_test_server.protocol.catalog import Arguments, Tool, text_content
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface
from mcp_test_server.transport.sessions import SessionWeakness
from mcp_test_server.vulns.weakness_vulnerability import WeaknessVulnerability
from mcp_test_server.weakness import WeaknessSet

NO_ARGUMENTS = {"type": "object", "properties": {}}


class CrossIdentitySession(WeaknessVulnerability):
    name = "cross_identity_session"
    summary = ("An Mcp-Session-Id opened by one user runs another user's requests in the owner's session context "
               "(the MCP Python SDK CVE-2026-52869 shape)")
    weakness = SessionWeakness.UNBOUND_SESSION

    def contribute(self, surface: ServerSurface) -> None:
        super().contribute(surface)
        access = Access(surface.environment.users)
        surface.catalog.add_tool(Tool("whoami", "Name the user this session belongs to", NO_ARGUMENTS,
                                      access.tool(_whoami), visible_to=access.any_user()))

    def weakness_set(self, surface: ServerSurface) -> WeaknessSet[Any]:
        return surface.session_weaknesses


async def _whoami(arguments: Arguments, user: User, context: CallContext) -> JsonObject:
    return text_content(context.session_owner or user.name)
