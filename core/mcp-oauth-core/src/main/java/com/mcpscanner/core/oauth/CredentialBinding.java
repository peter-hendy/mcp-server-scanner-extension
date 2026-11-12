package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.Set;

public record CredentialBinding(URI issuer, URI resource, String clientId, Set<String> scopes, OAuthGrantMode grantMode) {
    public CredentialBinding { scopes = Set.copyOf(scopes); java.util.Objects.requireNonNull(grantMode); }
    public CredentialBinding(URI issuer, URI resource, String clientId, Set<String> scopes) {
        this(issuer, resource, clientId, scopes, OAuthGrantMode.AUTHORIZATION_CODE);
    }
    @Override public boolean equals(Object other) {
        return other instanceof CredentialBinding binding && issuer.toString().equals(binding.issuer.toString())
                && resource.toString().equals(binding.resource.toString()) && clientId.equals(binding.clientId) && scopes.equals(binding.scopes) && grantMode == binding.grantMode;
    }
    @Override public int hashCode() { return java.util.Objects.hash(issuer.toString(), resource.toString(), clientId, scopes, grantMode); }
}
