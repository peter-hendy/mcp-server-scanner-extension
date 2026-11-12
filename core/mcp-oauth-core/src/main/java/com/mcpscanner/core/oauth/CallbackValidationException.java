package com.mcpscanner.core.oauth;

public final class CallbackValidationException extends IllegalArgumentException {
    public enum Reason { STATE_MISMATCH, MISSING_ISSUER, ISSUER_MISMATCH }
    private final Reason reason;

    public CallbackValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
