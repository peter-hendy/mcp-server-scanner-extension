from __future__ import annotations

from mcp_test_server.oauth.token_verifier import JwtWeakness
from mcp_test_server.vulns.jwt.jwt_vulnerability import JwtVulnerability


class JwtHs256KeyConfusion(JwtVulnerability):
    name = "jwt_hs256_key_confusion"
    summary = "HS256 tokens HMAC-signed with the server's RSA public key PEM are accepted"
    weakness = JwtWeakness.HS256_KEY_CONFUSION
