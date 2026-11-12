from __future__ import annotations

from mcp_test_server.oauth.refresh_policy import RefreshWeakness
from mcp_test_server.vulns.refresh.refresh_vulnerability import RefreshVulnerability


class RefreshScopeExpansion(RefreshVulnerability):
    name = "refresh_scope_expansion"
    summary = "Refresh grant issues scopes beyond the original grant"
    weakness = RefreshWeakness.SCOPE_EXPANSION
