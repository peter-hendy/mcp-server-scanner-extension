package com.mcpscanner.core.oauth;

public record AuthorizationCallback(String state, String issuer, String code, String error, String errorDescription) {
    @Override public String toString() { return "AuthorizationCallback[redacted]"; }
}
