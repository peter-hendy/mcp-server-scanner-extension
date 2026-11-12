from __future__ import annotations

from mcp_test_server.vulns.consent.consent_reflection import ConsentReflection


class ConsentStateReflection(ConsentReflection):
    name = "consent_state_reflection"
    summary = "Consent page reflects state unencoded in HTML text, a quoted attribute and an inline script"
    field = "state"
