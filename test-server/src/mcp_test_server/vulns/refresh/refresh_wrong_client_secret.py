from __future__ import annotations

from mcp_test_server.oauth.refresh_policy import RefreshWeakness
from mcp_test_server.vulns.refresh.refresh_vulnerability import RefreshVulnerability


class RefreshWrongClientSecret(RefreshVulnerability):
    name = "refresh_wrong_client_secret"
    summary = "Refresh grant for a confidential client succeeds with a wrong client secret"
    weakness = RefreshWeakness.WRONG_CLIENT_SECRET
