from __future__ import annotations

from typing import Any

from mcp_test_server.oauth.credential_gate import AuthWeakness
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.weakness_vulnerability import WeaknessVulnerability
from mcp_test_server.weakness import WeaknessSet


class AuthForwardedLoopbackTrust(WeaknessVulnerability):
    name = "auth_forwarded_loopback_trust"
    summary = "Requests without credentials execute when X-Forwarded-For or X-Real-IP names a loopback address"
    weakness = AuthWeakness.FORWARDED_LOOPBACK_TRUST

    def weakness_set(self, surface: ServerSurface) -> WeaknessSet[Any]:
        return surface.auth_weaknesses
