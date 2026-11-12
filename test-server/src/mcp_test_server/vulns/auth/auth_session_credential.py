from __future__ import annotations

from typing import Any

from mcp_test_server.oauth.credential_gate import AuthWeakness
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.weakness_vulnerability import WeaknessVulnerability
from mcp_test_server.weakness import WeaknessSet


class AuthSessionCredential(WeaknessVulnerability):
    name = "auth_session_credential"
    summary = "An Mcp-Session-Id once used with a valid bearer token authenticates later requests on its own"
    weakness = AuthWeakness.SESSION_CREDENTIAL

    def weakness_set(self, surface: ServerSurface) -> WeaknessSet[Any]:
        return surface.auth_weaknesses
