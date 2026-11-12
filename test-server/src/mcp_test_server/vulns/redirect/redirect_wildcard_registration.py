from __future__ import annotations

from mcp_test_server.oauth.redirect_policy import RedirectWeakness
from mcp_test_server.vulns.redirect.redirect_vulnerability import RedirectVulnerability


class RedirectWildcardRegistration(RedirectVulnerability):
    name = "redirect_wildcard_registration"
    summary = "DCR accepts wildcard-host redirect_uris and /authorize matches them"
    weakness = RedirectWeakness.WILDCARD_REGISTRATION
