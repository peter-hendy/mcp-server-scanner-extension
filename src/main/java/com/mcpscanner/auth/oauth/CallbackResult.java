package com.mcpscanner.auth.oauth;

public record CallbackResult(String code, String state, String error, String errorDescription, String issuer) {

    public CallbackResult(String code, String state, String error, String errorDescription) {
        this(code, state, error, errorDescription, null);
    }

    public boolean isError() {
        return error != null;
    }
}
