from __future__ import annotations

from typing import Any

from mcp_test_server.oauth.credential_gate import AuthWeakness
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.weakness_vulnerability import WeaknessVulnerability
from mcp_test_server.weakness import WeaknessSet


class AuthSiblingRoute(WeaknessVulnerability):
    name = "auth_sibling_route"
    summary = "The Streamable HTTP handler is also mounted on /message, where requests without credentials execute"
    weakness = AuthWeakness.UNGUARDED_SIBLING_ROUTE

    def weakness_set(self, surface: ServerSurface) -> WeaknessSet[Any]:
        return surface.auth_weaknesses
