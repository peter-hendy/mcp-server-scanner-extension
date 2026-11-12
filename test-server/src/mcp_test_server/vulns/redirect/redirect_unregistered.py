from __future__ import annotations

from mcp_test_server.oauth.redirect_policy import RedirectWeakness
from mcp_test_server.vulns.redirect.redirect_vulnerability import RedirectVulnerability


class RedirectUnregistered(RedirectVulnerability):
    name = "redirect_unregistered"
    summary = "/authorize redirects to a redirect_uri the client never registered"
    weakness = RedirectWeakness.UNREGISTERED
