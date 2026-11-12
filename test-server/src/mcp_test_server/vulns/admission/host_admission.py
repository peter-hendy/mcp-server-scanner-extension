from __future__ import annotations

from urllib.parse import urlsplit

from mcp_test_server.vulns.admission.header_admission import HeaderAdmission


class HostAdmission(HeaderAdmission):
    name = "host_admission"
    summary = "MCP endpoint executes requests carrying a foreign Host header (DNS-rebinding shape)"
    header = "host"

    def allowed(self, value: str) -> bool:
        return self.host_allowed(urlsplit(f"//{value}").hostname)
