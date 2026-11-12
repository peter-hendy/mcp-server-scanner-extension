package com.mcpscanner.core.oauth;

import java.util.Map;

public record TokenResponse(int status, Map<String, Object> fields) {
    public TokenResponse { fields = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields)); }
    @Override public String toString() { return "TokenResponse[status=" + status + ", fields=redacted]"; }
}
