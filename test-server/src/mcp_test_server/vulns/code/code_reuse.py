from __future__ import annotations

from mcp_test_server.oauth.grants import CodeWeakness
from mcp_test_server.vulns.code.code_vulnerability import CodeVulnerability


class CodeReuse(CodeVulnerability):
    name = "code_reuse"
    summary = "Token endpoint redeems an authorization code again after it was already redeemed"
    weakness = CodeWeakness.REUSE
