from __future__ import annotations

from mcp_test_server.oauth.redirect_policy import RedirectWeakness
from mcp_test_server.vulns.redirect.redirect_vulnerability import RedirectVulnerability


class RedirectPathNormalisation(RedirectVulnerability):
    name = "redirect_path_normalisation"
    summary = "/authorize accepts redirect_uris that extend or dot-segment-normalise to a registered one"
    weakness = RedirectWeakness.PATH_NORMALISATION
