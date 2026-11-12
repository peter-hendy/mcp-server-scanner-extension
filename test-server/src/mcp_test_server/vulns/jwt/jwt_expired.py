from __future__ import annotations

from mcp_test_server.oauth.token_verifier import JwtWeakness
from mcp_test_server.vulns.jwt.jwt_vulnerability import JwtVulnerability


class JwtExpired(JwtVulnerability):
    name = "jwt_expired"
    summary = "Expired tokens (or tokens without exp) are accepted"
    weakness = JwtWeakness.EXPIRED
