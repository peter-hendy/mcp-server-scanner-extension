package com.mcpscanner.core.oauth;

import java.net.URI;

public final class AuthorizationCallbackValidatorTest {
    private static final URI ISSUER = URI.create("https://issuer.example/tenant");
    private static final String STATE = "unguessable-state";

    public static void main(String[] args) {
        acceptsMatchingIssuerAndState();
        rejectsMissingOrDifferentState();
        requiresAdvertisedIssuer();
        validatesEveryPresentIssuerExactly();
        validatesIssuerBeforeReturningAuthorizationErrors();
        validatesStateBeforeReturningAuthorizationErrors();
        System.out.println("AuthorizationCallbackValidatorTest: 6 behavior groups passed");
    }

    private static void acceptsMatchingIssuerAndState() {
        var callback = new AuthorizationCallback(STATE, ISSUER.toString(), "code", null, null);
        var result = new AuthorizationCallbackValidator().validate(STATE, ISSUER, true, callback);
        require(result.equals(callback), "Validated callback retains authorization code");
    }

    private static void rejectsMissingOrDifferentState() {
        for (String state : new String[]{null, "", "other", STATE.toUpperCase()}) {
            var callback = new AuthorizationCallback(state, ISSUER.toString(), "code", null, null);
            expect(CallbackValidationException.Reason.STATE_MISMATCH,
                    () -> new AuthorizationCallbackValidator().validate(STATE, ISSUER, true, callback));
        }
        expect(CallbackValidationException.Reason.STATE_MISMATCH,
                () -> new AuthorizationCallbackValidator().validate(null, ISSUER, true,
                        new AuthorizationCallback(null, ISSUER.toString(), "code", null, null)));
    }

    private static void requiresAdvertisedIssuer() {
        var callback = new AuthorizationCallback(STATE, null, "code", null, null);
        expect(CallbackValidationException.Reason.MISSING_ISSUER,
                () -> new AuthorizationCallbackValidator().validate(STATE, ISSUER, true, callback));
        var result = new AuthorizationCallbackValidator().validate(STATE, ISSUER, false, callback);
        require(result.equals(callback), "Issuer may be absent when not advertised");
    }

    private static void validatesEveryPresentIssuerExactly() {
        for (boolean supported : new boolean[]{true, false}) {
            var matching = new AuthorizationCallback(STATE, ISSUER.toString(), "code", null, null);
            require(new AuthorizationCallbackValidator().validate(STATE, ISSUER, supported, matching).equals(matching),
                    "Matching issuer accepted regardless of metadata support flag");
            for (String issuer : new String[]{"", "https://other.example/tenant", "https://issuer.example/tenant/", "https://ISSUER.example/tenant"}) {
                var callback = new AuthorizationCallback(STATE, issuer, "code", null, null);
                expect(CallbackValidationException.Reason.ISSUER_MISMATCH,
                        () -> new AuthorizationCallbackValidator().validate(STATE, ISSUER, supported, callback));
            }
        }
    }

    private static void validatesIssuerBeforeReturningAuthorizationErrors() {
        for (boolean supported : new boolean[]{true, false}) {
            var error = new AuthorizationCallback(STATE, ISSUER.toString(), null, "access_denied", "User declined");
            var result = new AuthorizationCallbackValidator().validate(STATE, ISSUER, supported, error);
            require(result.error().equals("access_denied") && result.errorDescription().equals("User declined"),
                    "Validated authorization error retained");
            var wrongIssuer = new AuthorizationCallback(STATE, "https://other.example", null, "access_denied", "Untrusted error text");
            expect(CallbackValidationException.Reason.ISSUER_MISMATCH,
                    () -> new AuthorizationCallbackValidator().validate(STATE, ISSUER, supported, wrongIssuer));
        }
        var noIssuer = new AuthorizationCallback(STATE, null, null, "access_denied", "User declined");
        expect(CallbackValidationException.Reason.MISSING_ISSUER,
                () -> new AuthorizationCallbackValidator().validate(STATE, ISSUER, true, noIssuer));
        require(new AuthorizationCallbackValidator().validate(STATE, ISSUER, false, noIssuer).equals(noIssuer),
                "Error callback may omit issuer when not advertised");
    }

    private static void validatesStateBeforeReturningAuthorizationErrors() {
        for (boolean supported : new boolean[]{true, false}) {
            var callback = new AuthorizationCallback("wrong-state", ISSUER.toString(), null, "access_denied", "Untrusted error text");
            expect(CallbackValidationException.Reason.STATE_MISMATCH,
                    () -> new AuthorizationCallbackValidator().validate(STATE, ISSUER, supported, callback));
        }
    }

    private static void expect(CallbackValidationException.Reason reason, Runnable action) {
        try {
            action.run();
        } catch (CallbackValidationException e) {
            require(e.reason() == reason, "Expected " + reason + " but got " + e.reason());
            require(!e.getMessage().contains("Untrusted error text"), "Rejected callback error text must not be exposed");
            return;
        }
        throw new AssertionError("Expected " + reason);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
