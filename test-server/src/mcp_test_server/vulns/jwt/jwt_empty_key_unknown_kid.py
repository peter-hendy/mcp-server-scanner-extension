from __future__ import annotations

from mcp_test_server.oauth.token_verifier import JwtWeakness
from mcp_test_server.vulns.jwt.jwt_vulnerability import JwtVulnerability


class JwtEmptyKeyUnknownKid(JwtVulnerability):
    name = "jwt_empty_key_unknown_kid"
    summary = "HS256 tokens with an unknown kid verify against an empty HMAC key (fast-jwt CVE-2026-44351 shape)"
    weakness = JwtWeakness.EMPTY_KEY_UNKNOWN_KID
