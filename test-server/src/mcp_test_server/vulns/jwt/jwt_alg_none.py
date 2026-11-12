from __future__ import annotations

from mcp_test_server.oauth.token_verifier import JwtWeakness
from mcp_test_server.vulns.jwt.jwt_vulnerability import JwtVulnerability


class JwtAlgNone(JwtVulnerability):
    name = "jwt_alg_none"
    summary = "Unsigned alg:none bearer tokens are accepted"
    weakness = JwtWeakness.ALG_NONE
