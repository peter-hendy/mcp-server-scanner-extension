package com.mcpscanner.core.oauth;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OAuthFlowEngine {
    private final TokenExchange exchange;
    private final BrowserAuthorization browser;
    private final CredentialStore store;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OAuthFlowEngine(TokenExchange exchange, BrowserAuthorization browser, CredentialStore store, Clock clock) {
        this.exchange = java.util.Objects.requireNonNull(exchange);
        this.browser = java.util.Objects.requireNonNull(browser);
        this.store = java.util.Objects.requireNonNull(store);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public OAuthCredential authenticate(AuthorizationCodeConfig config) {
        new FlowConfigurationValidator().validate(config);
        var form = new AuthorizationCodeAttempt(config, random).authorize(browser);
        return exchangeAndSave(config, form, null);
    }

    public OwnedCodeExchangeResult exchangeOwnedCode(AuthorizationCodeConfig config,
            PkceChallenge challengeMode, VerifierSubmission verifierSubmission) {
        return exchangeOwnedCode(config, challengeMode, verifierSubmission, accessToken -> false);
    }

    public OwnedCodeExchangeResult exchangeOwnedCode(AuthorizationCodeConfig config,
            PkceChallenge challengeMode, VerifierSubmission verifierSubmission,
            OwnedTokenVerifier tokenVerifier) {
        java.util.Objects.requireNonNull(tokenVerifier);
        new FlowConfigurationValidator().validate(config);
        Map<String, String> form;
        try {
            form = new AuthorizationCodeAttempt(config, random, challengeMode, verifierSubmission).authorize(browser);
        } catch (CallbackValidationException invalidCallback) {
            throw new OAuthFlowException(OAuthFlowException.Reason.INVALID_CALLBACK, "Authorization callback is invalid");
        }
        var request = new TokenEndpointRequestFactory().create(config.tokenEndpoint(), form, config.client(),
            config.tokenEndpointAuthMethod(), clock);
        TokenResponse response;
        try { response = exchange.exchange(request); }
        catch (RuntimeException failure) {
            throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED, "Token endpoint exchange failed");
        }
        if (response == null) {
            throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED, "Token endpoint exchange failed");
        }
        try {
            OAuthCredential credential = new TokenResponseValidator().validate(response, config.binding(), null, clock);
            boolean verified;
            try { verified = tokenVerifier.verify(credential.accessToken()); }
            catch (RuntimeException failure) {
                throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED,
                    "Protected operation verification failed");
            }
            return new OwnedCodeExchangeResult(response.status(), true, verified);
        } catch (OAuthFlowException invalidResponse) {
            if (invalidResponse.reason() == OAuthFlowException.Reason.EXCHANGE_FAILED) throw invalidResponse;
            return OwnedCodeExchangeResult.denied(response);
        }
    }

    public OAuthCredential refresh(AuthorizationCodeConfig config) {
        new FlowConfigurationValidator().validate(config);
        var previous = load(config.binding());
        if (!previous.binding().equals(config.binding()) || previous.refreshToken() == null) {
            throw new OAuthFlowException(OAuthFlowException.Reason.MISSING_CREDENTIAL, "No refresh token is available for this binding");
        }
        var form = new LinkedHashMap<String, String>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", previous.refreshToken());
        form.put("resource", config.resource().toString());
        return exchangeAndSave(config, form, previous);
    }

    public OwnedRefreshExchangeResult exchangeOwnedRefresh(AuthorizationCodeConfig config,
            String refreshToken, RefreshClientAuthentication authentication,
            java.util.Set<String> requestedScopes, OwnedTokenVerifier tokenVerifier) {
        new FlowConfigurationValidator().validate(config);
        java.util.Objects.requireNonNull(authentication);
        java.util.Objects.requireNonNull(requestedScopes);
        java.util.Objects.requireNonNull(tokenVerifier);
        if (config.client().clientSecret() == null || config.client().clientSecret().isBlank()
            || refreshToken == null || refreshToken.isBlank()
            || refreshToken.chars().anyMatch(character -> character < 0x21 || character > 0x7e)
            || requestedScopes.stream().anyMatch(scope -> !ScopeValues.valid(scope))) {
            throw new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION,
                "Owned refresh probe configuration is invalid");
        }
        var form = new LinkedHashMap<String, String>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("resource", config.resource().toString());
        if (!requestedScopes.isEmpty()) {
            form.put("scope", String.join(" ", new java.util.TreeSet<>(requestedScopes)));
        }
        String method = authentication == RefreshClientAuthentication.OMIT
            ? "none" : config.tokenEndpointAuthMethod();
        TokenRequest request = new TokenEndpointRequestFactory().create(config.tokenEndpoint(), form,
            config.client(), method, clock);
        TokenResponse response;
        try { response = exchange.exchange(request); }
        catch (RuntimeException failure) {
            throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED,
                "Token endpoint exchange failed");
        }
        if (response == null) {
            throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED,
                "Token endpoint exchange failed");
        }
        try {
            OAuthCredential credential = new TokenResponseValidator().validate(response, config.binding(), null, clock);
            boolean verified;
            try { verified = tokenVerifier.verify(credential.accessToken()); }
            catch (RuntimeException failure) {
                throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED,
                    "Protected operation verification failed");
            }
            return new OwnedRefreshExchangeResult(response.status(), true,
                credential.grantedScopes(), verified);
        } catch (OAuthFlowException invalidResponse) {
            if (invalidResponse.reason() == OAuthFlowException.Reason.EXCHANGE_FAILED) throw invalidResponse;
            return new OwnedRefreshExchangeResult(response.status(), false, java.util.Set.of(), false);
        }
    }

    private OAuthCredential exchangeAndSave(AuthorizationCodeConfig config, Map<String, String> form, OAuthCredential previous) {
        var request = new TokenEndpointRequestFactory().create(config.tokenEndpoint(), form, config.client(),
                config.tokenEndpointAuthMethod(), clock);
        TokenResponse response;
        try { response = exchange.exchange(request); }
        catch (RuntimeException failure) {
            throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED, "Token endpoint exchange failed");
        }
        var credential = new TokenResponseValidator().validate(response, config.binding(), previous, clock);
        try { store.save(config.binding(), credential); }
        catch (RuntimeException failure) {
            throw new OAuthFlowException(OAuthFlowException.Reason.STORAGE_FAILED, "Credential storage failed");
        }
        return credential;
    }

    private OAuthCredential load(CredentialBinding binding) {
        java.util.Optional<OAuthCredential> credential;
        try { credential = store.load(binding); }
        catch (RuntimeException failure) {
            throw new OAuthFlowException(OAuthFlowException.Reason.STORAGE_FAILED, "Credential storage failed");
        }
        return credential.orElseThrow(() -> new OAuthFlowException(OAuthFlowException.Reason.MISSING_CREDENTIAL,
                "No credential is available for this binding"));
    }

}
