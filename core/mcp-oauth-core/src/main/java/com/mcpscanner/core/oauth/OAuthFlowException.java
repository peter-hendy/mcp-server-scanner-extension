package com.mcpscanner.core.oauth;

public final class OAuthFlowException extends RuntimeException {
    public enum Reason { INVALID_CONFIGURATION, INVALID_CALLBACK, AUTHORIZATION_FAILED, AUTHORIZATION_DENIED, INVALID_TOKEN_RESPONSE, EXCHANGE_FAILED, MISSING_CREDENTIAL, STORAGE_FAILED }
    private final Reason reason;
    public OAuthFlowException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
}
