package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.Set;

public record AuthorizationCodeConfig(URI issuer, URI resource, URI authorizationEndpoint,
        URI tokenEndpoint, RegisteredClient client, URI callbackUri, Set<String> scopes,
        boolean issuerParameterSupported, String tokenEndpointAuthMethod) {
    public AuthorizationCodeConfig(URI issuer, URI resource, URI authorizationEndpoint, URI tokenEndpoint,
            RegisteredClient client, URI callbackUri, Set<String> scopes, boolean issuerParameterSupported) {
        this(issuer, resource, authorizationEndpoint, tokenEndpoint, client, callbackUri, scopes,
                issuerParameterSupported, client != null && client.clientSecret() != null ? "client_secret_basic" : "none");
    }
    public AuthorizationCodeConfig { scopes = Set.copyOf(scopes); }
    public CredentialBinding binding() { return new CredentialBinding(issuer, resource, client.clientId(), scopes); }
    @Override public String toString() { return "AuthorizationCodeConfig[redacted]"; }
}
