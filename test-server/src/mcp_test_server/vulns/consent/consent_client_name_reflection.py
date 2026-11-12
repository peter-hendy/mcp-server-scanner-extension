from __future__ import annotations

from mcp_test_server.vulns.consent.consent_reflection import ConsentReflection


class ConsentClientNameReflection(ConsentReflection):
    name = "consent_client_name_reflection"
    summary = "Consent page reflects client_name unencoded in HTML text, a quoted attribute and an inline script"
    field = "client_name"
