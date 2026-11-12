from __future__ import annotations

from typing import Any

from mcp_test_server.oauth.credential_gate import AuthWeakness
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.weakness_vulnerability import WeaknessVulnerability
from mcp_test_server.weakness import WeaknessSet


class AuthPresenceOnlyCredential(WeaknessVulnerability):
    name = "auth_presence_only_credential"
    summary = "Any non-empty bearer token is accepted; the gate checks that a credential is present, not its value"
    weakness = AuthWeakness.PRESENCE_ONLY_CREDENTIAL

    def weakness_set(self, surface: ServerSurface) -> WeaknessSet[Any]:
        return surface.auth_weaknesses
