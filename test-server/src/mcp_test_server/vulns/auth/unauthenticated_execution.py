from __future__ import annotations

from typing import Any

from mcp_test_server.oauth.credential_gate import AuthWeakness
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.weakness_vulnerability import WeaknessVulnerability
from mcp_test_server.weakness import WeaknessSet


class UnauthenticatedExecution(WeaknessVulnerability):
    name = "unauthenticated_execution"
    summary = "MCP endpoints execute every method (requests and notifications) without a credential"
    weakness = AuthWeakness.ANONYMOUS_ACCESS

    def weakness_set(self, surface: ServerSurface) -> WeaknessSet[Any]:
        return surface.auth_weaknesses
