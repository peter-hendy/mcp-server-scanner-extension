package com.mcpscanner.core.oauth;

import java.util.Map;

public record EnterpriseIdentityRequest(Map<String, String> form) {
    public EnterpriseIdentityRequest { form = Map.copyOf(form); }
    @Override public String toString() { return "EnterpriseIdentityRequest[redacted]"; }
}
