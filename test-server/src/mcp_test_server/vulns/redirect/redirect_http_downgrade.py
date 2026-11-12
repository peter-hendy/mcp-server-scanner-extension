from __future__ import annotations

from mcp_test_server.oauth.redirect_policy import RedirectWeakness
from mcp_test_server.vulns.redirect.redirect_vulnerability import RedirectVulnerability


class RedirectHttpDowngrade(RedirectVulnerability):
    name = "redirect_http_downgrade"
    summary = "/authorize accepts the http:// twin of a registered https:// redirect_uri"
    weakness = RedirectWeakness.HTTP_DOWNGRADE
