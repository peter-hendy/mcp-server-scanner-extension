from __future__ import annotations

from urllib.parse import urlsplit

from mcp_test_server.vulns.admission.header_admission import HeaderAdmission


class OriginAdmission(HeaderAdmission):
    name = "origin_admission"
    summary = "MCP endpoint executes requests carrying a hostile Origin header (DNS-rebinding / CSRF shape)"
    header = "origin"

    def allowed(self, value: str) -> bool:
        parts = urlsplit(value)
        return parts.scheme in {"http", "https"} and self.host_allowed(parts.hostname)
