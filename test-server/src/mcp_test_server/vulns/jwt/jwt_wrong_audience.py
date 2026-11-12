from __future__ import annotations

from mcp_test_server.oauth.token_verifier import JwtWeakness
from mcp_test_server.vulns.jwt.jwt_vulnerability import JwtVulnerability


class JwtWrongAudience(JwtVulnerability):
    name = "jwt_wrong_audience"
    summary = "Tokens issued for another audience are accepted"
    weakness = JwtWeakness.WRONG_AUDIENCE
