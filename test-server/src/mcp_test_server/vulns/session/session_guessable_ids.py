from __future__ import annotations

from typing import Any

from mcp_test_server.surface import ServerSurface
from mcp_test_server.transport.sessions import SessionWeakness
from mcp_test_server.vulns.weakness_vulnerability import WeaknessVulnerability
from mcp_test_server.weakness import WeaknessSet


class SessionGuessableIds(WeaknessVulnerability):
    name = "session_guessable_ids"
    summary = "Mcp-Session-Id values come from a sequential counter, so the next session's ID is predictable"
    weakness = SessionWeakness.GUESSABLE_IDS

    def weakness_set(self, surface: ServerSurface) -> WeaknessSet[Any]:
        return surface.session_weaknesses
