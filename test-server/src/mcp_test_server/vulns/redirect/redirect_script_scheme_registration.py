from __future__ import annotations

from mcp_test_server.oauth.redirect_policy import RedirectWeakness
from mcp_test_server.vulns.redirect.redirect_vulnerability import RedirectVulnerability


class RedirectScriptSchemeRegistration(RedirectVulnerability):
    name = "redirect_script_scheme_registration"
    summary = "DCR accepts javascript:, data: and vbscript: redirect_uris"
    weakness = RedirectWeakness.SCRIPT_SCHEME_REGISTRATION
