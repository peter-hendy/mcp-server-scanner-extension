from __future__ import annotations

from mcp_test_server.oauth.token_verifier import JwtWeakness
from mcp_test_server.vulns.jwt.jwt_vulnerability import JwtVulnerability


class JwtWrongIssuer(JwtVulnerability):
    name = "jwt_wrong_issuer"
    summary = "Tokens from an untrusted issuer are accepted"
    weakness = JwtWeakness.WRONG_ISSUER
