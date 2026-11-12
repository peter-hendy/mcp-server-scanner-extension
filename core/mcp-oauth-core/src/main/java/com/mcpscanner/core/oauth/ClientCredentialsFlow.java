package com.mcpscanner.core.oauth;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class ClientCredentialsFlow {
    private final TokenExchange exchange;
    private final CredentialStore store;
    private final Clock clock;
    private final OAuthTokenRefreshPolicy refreshPolicy;

    public ClientCredentialsFlow(TokenExchange exchange, CredentialStore store, Clock clock) {
        this.exchange = Objects.requireNonNull(exchange);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.refreshPolicy = new OAuthTokenRefreshPolicy(clock);
    }

    public synchronized OAuthCredential credential(ClientCredentialsConfig config) {
        new ClientCredentialsValidator().validate(config);
        java.util.Optional<OAuthCredential> saved;
        try { saved = store.load(config.binding()); }
        catch (RuntimeException failure) { throw new OAuthFlowException(OAuthFlowException.Reason.STORAGE_FAILED, "Credential storage failed"); }
        if (saved.isPresent()) {
            var value = saved.get();
            if (value.binding().equals(config.binding()) && !refreshPolicy.needsRefresh(value.expiresAt())) return value;
        }
        return authenticate(config);
    }

    public synchronized OAuthCredential authenticate(ClientCredentialsConfig config) {
        new ClientCredentialsValidator().validate(config);
        var form = new LinkedHashMap<String, String>();
        form.put("grant_type", "client_credentials");
        form.put("resource", config.binding().resource().toString());
        if (!config.binding().scopes().isEmpty()) form.put("scope", String.join(" ", new java.util.TreeSet<>(config.binding().scopes())));
        var request = new TokenEndpointRequestFactory().create(config.serverMetadata().tokenEndpoint(), form, config.client(), config.authentication(), clock);
        TokenResponse response;
        try { response = exchange.exchange(request); }
        catch (RuntimeException failure) { throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED, "Token endpoint exchange failed"); }
        var credential = new TokenResponseValidator().validate(response, config.binding(), null, clock);
        credential = new OAuthCredential(credential.binding(), credential.accessToken(), null, credential.expiresAt(), credential.grantedScopes());
        try { store.save(config.binding(), credential); }
        catch (RuntimeException failure) { throw new OAuthFlowException(OAuthFlowException.Reason.STORAGE_FAILED, "Credential storage failed"); }
        return credential;
    }
}
