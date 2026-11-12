package com.mcpscanner.core.oauth;

public final class OAuthPolicyException extends RuntimeException {
    public enum Reason { INVALID_CHALLENGE, INVALID_METADATA, RESOURCE_MISMATCH, ISSUER_MISMATCH, INVALID_RETRY_STATE }
    private final Reason reason;
    public OAuthPolicyException(Reason reason) {
        super("OAuth policy rejected input: " + reason.name());
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
