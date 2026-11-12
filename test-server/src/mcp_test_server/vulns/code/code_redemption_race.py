from __future__ import annotations

from mcp_test_server.oauth.grants import CodeWeakness
from mcp_test_server.vulns.code.code_vulnerability import CodeVulnerability


class CodeRedemptionRace(CodeVulnerability):
    name = "code_redemption_race"
    summary = "Token endpoint finds then deletes a code non-atomically, so overlapping redemptions all succeed"
    weakness = CodeWeakness.REDEMPTION_RACE
