package com.mcpscanner.core.oauth;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EnterpriseManagedFlow {
    private final EnterpriseIdentityProvider identityProvider;
    private final TokenExchange exchange;
    private final CredentialStore store;
    private final Clock clock;
    private final OAuthTokenRefreshPolicy refreshPolicy;

    public EnterpriseManagedFlow(EnterpriseIdentityProvider identityProvider, TokenExchange exchange, CredentialStore store, Clock clock) {
        this.identityProvider = Objects.requireNonNull(identityProvider);
        this.exchange = Objects.requireNonNull(exchange);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.refreshPolicy = new OAuthTokenRefreshPolicy(clock);
    }

    public synchronized OAuthCredential credential(EnterpriseManagedConfig config) {
        new EnterpriseConfigurationValidator().validate(config);
        java.util.Optional<OAuthCredential> saved;
        try { saved = store.load(config.binding()); }
        catch (RuntimeException failure) { throw storageFailure(); }
        if (saved.isPresent()) {
            var value = saved.get();
            if (value.binding().equals(config.binding()) && !refreshPolicy.needsRefresh(value.expiresAt())) return value;
        }
        return authenticate(config);
    }

    public synchronized OAuthCredential authenticate(EnterpriseManagedConfig config) {
        new EnterpriseConfigurationValidator().validate(config);
        var form = new LinkedHashMap<String, String>();
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.put("requested_token_type", "urn:ietf:params:oauth:token-type:id-jag");
        form.put("audience", config.binding().issuer().toString());
        if (config.includeResource()) form.put("resource", config.binding().resource().toString());
        if (!config.binding().scopes().isEmpty()) form.put("scope", String.join(" ", new java.util.TreeSet<>(config.binding().scopes())));
        TokenResponse identity;
        try { identity = identityProvider.exchange(new EnterpriseIdentityRequest(form)); }
        catch (RuntimeException failure) { throw exchangeFailure(); }
        String assertion = new EnterpriseIdentityResponseValidator().validate(identity, config.binding(), clock);
        var request = new TokenEndpointRequestFactory().create(config.serverMetadata().tokenEndpoint(),
                Map.of("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion", assertion),
                config.client(), config.authentication(), clock);
        TokenResponse response;
        try { response = exchange.exchange(request); }
        catch (RuntimeException failure) { throw exchangeFailure(); }
        var credential = new TokenResponseValidator().validate(response, config.binding(), null, clock);
        credential = new OAuthCredential(credential.binding(), credential.accessToken(), null, credential.expiresAt(), credential.grantedScopes());
        try { store.save(config.binding(), credential); }
        catch (RuntimeException failure) { throw storageFailure(); }
        return credential;
    }

    private OAuthFlowException storageFailure() {
        return new OAuthFlowException(OAuthFlowException.Reason.STORAGE_FAILED, "Credential storage failed");
    }

    private OAuthFlowException exchangeFailure() {
        return new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED, "Enterprise token exchange failed");
    }
}
