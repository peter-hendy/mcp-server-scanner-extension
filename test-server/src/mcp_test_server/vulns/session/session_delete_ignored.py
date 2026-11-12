from __future__ import annotations

from typing import Any

from mcp_test_server.surface import ServerSurface
from mcp_test_server.transport.sessions import SessionWeakness
from mcp_test_server.vulns.weakness_vulnerability import WeaknessVulnerability
from mcp_test_server.weakness import WeaknessSet


class SessionDeleteIgnored(WeaknessVulnerability):
    name = "session_delete_ignored"
    summary = "DELETE on /mcp answers 200 but leaves the session usable"
    weakness = SessionWeakness.DELETE_IGNORED

    def weakness_set(self, surface: ServerSurface) -> WeaknessSet[Any]:
        return surface.session_weaknesses
