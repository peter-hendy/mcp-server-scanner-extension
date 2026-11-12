package com.mcpscanner.core.oauth;

public final class RegistrationException extends IllegalArgumentException {
    public enum Reason { MISSING_REGISTRATION, UNSUPPORTED_MODE, ISSUER_MISMATCH, INVALID_CLIENT, INVALID_CIMD_URL, INVALID_CALLBACK, INVALID_CIMD_DOCUMENT }
    private final Reason reason;
    public RegistrationException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
}
