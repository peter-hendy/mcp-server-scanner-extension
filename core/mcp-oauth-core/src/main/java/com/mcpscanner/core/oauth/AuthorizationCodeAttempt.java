package com.mcpscanner.core.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

final class AuthorizationCodeAttempt {
    private final AuthorizationCodeConfig config;
    private final String verifier;
    private final String state;
    private final PkceChallenge challengeMode;
    private final VerifierSubmission verifierSubmission;
    private final String mismatchedVerifier;

    AuthorizationCodeAttempt(AuthorizationCodeConfig config, SecureRandom random) {
        this(config, random, PkceChallenge.S256, VerifierSubmission.MATCHING);
    }

    AuthorizationCodeAttempt(AuthorizationCodeConfig config, SecureRandom random,
            PkceChallenge challengeMode, VerifierSubmission verifierSubmission) {
        this.config = config;
        this.verifier = randomValue(random);
        this.state = randomValue(random);
        this.challengeMode = java.util.Objects.requireNonNull(challengeMode);
        this.verifierSubmission = java.util.Objects.requireNonNull(verifierSubmission);
        this.mismatchedVerifier = verifierSubmission == VerifierSubmission.MISMATCHED ? randomValue(random) : null;
    }

    Map<String, String> authorize(BrowserAuthorization browser) {
        BrowserCallback callback;
        try { callback = browser.authorize(new AuthorizationRequest(config.authorizationEndpoint(), parameters())); }
        catch (RuntimeException failure) {
            throw new OAuthFlowException(OAuthFlowException.Reason.AUTHORIZATION_FAILED, "Browser authorization failed");
        }
        validate(callback);
        var form = new LinkedHashMap<String, String>();
        form.put("grant_type", "authorization_code");
        form.put("code", callback.parameters().code());
        form.put("redirect_uri", config.callbackUri().toString());
        form.put("resource", config.resource().toString());
        switch (verifierSubmission) {
            case MATCHING -> form.put("code_verifier", verifier);
            case MISMATCHED -> form.put("code_verifier", mismatchedVerifier);
            case OMIT -> { }
        }
        return form;
    }

    private Map<String, String> parameters() {
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("response_type", "code");
        parameters.put("client_id", config.client().clientId());
        parameters.put("redirect_uri", config.callbackUri().toString());
        parameters.put("resource", config.resource().toString());
        if (!config.scopes().isEmpty()) parameters.put("scope", String.join(" ", new TreeSet<>(config.scopes())));
        parameters.put("state", state);
        if (challengeMode == PkceChallenge.S256) {
            parameters.put("code_challenge_method", "S256");
            parameters.put("code_challenge", challenge());
        }
        return parameters;
    }

    private void validate(BrowserCallback callback) {
        if (callback == null || !config.callbackUri().toString().equals(String.valueOf(callback.callbackUri())) || callback.parameters() == null) {
            throw new OAuthFlowException(OAuthFlowException.Reason.INVALID_CALLBACK, "Authorization callback location does not match");
        }
        new AuthorizationCallbackValidator().validate(state, config.issuer(), config.issuerParameterSupported(), callback.parameters());
        if (callback.parameters().error() != null) {
            throw new OAuthFlowException(OAuthFlowException.Reason.AUTHORIZATION_DENIED, "Authorization was denied");
        }
        if (callback.parameters().code() == null || callback.parameters().code().isBlank()) {
            throw new OAuthFlowException(OAuthFlowException.Reason.INVALID_CALLBACK, "Authorization callback contains no code");
        }
    }

    private String randomValue(SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String challenge() {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
