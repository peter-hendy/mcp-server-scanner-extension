from __future__ import annotations

from mcp_test_server.oauth.token_verifier import JwtWeakness
from mcp_test_server.vulns.jwt.jwt_vulnerability import JwtVulnerability


class JwtForgedSignature(JwtVulnerability):
    name = "jwt_forged_signature"
    summary = "RS256 bearer tokens are accepted without verifying the signature"
    weakness = JwtWeakness.FORGED_SIGNATURE
