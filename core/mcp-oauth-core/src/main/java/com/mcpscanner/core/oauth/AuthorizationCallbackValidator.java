package com.mcpscanner.core.oauth;

import java.net.URI;

public final class AuthorizationCallbackValidator {
    public AuthorizationCallback validate(String expectedState, URI expectedIssuer,
            boolean issuerParameterSupported, AuthorizationCallback callback) {
        if (expectedState == null || expectedState.isBlank() || !expectedState.equals(callback.state())) {
            throw new CallbackValidationException(CallbackValidationException.Reason.STATE_MISMATCH, "Authorization callback state does not match");
        }
        if (issuerParameterSupported && callback.issuer() == null) {
            throw new CallbackValidationException(CallbackValidationException.Reason.MISSING_ISSUER, "Authorization callback issuer is required");
        }
        if (callback.issuer() != null && (expectedIssuer == null || !expectedIssuer.toString().equals(callback.issuer()))) {
            throw new CallbackValidationException(CallbackValidationException.Reason.ISSUER_MISMATCH, "Authorization callback issuer does not match");
        }
        return callback;
    }
}
