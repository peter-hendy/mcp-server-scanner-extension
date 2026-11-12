from __future__ import annotations

from mcp_test_server.oauth.refresh_policy import RefreshWeakness
from mcp_test_server.vulns.refresh.refresh_vulnerability import RefreshVulnerability


class RefreshMissingClientSecret(RefreshVulnerability):
    name = "refresh_missing_client_secret"
    summary = "Refresh grant for a confidential client succeeds without a client secret"
    weakness = RefreshWeakness.MISSING_CLIENT_SECRET
