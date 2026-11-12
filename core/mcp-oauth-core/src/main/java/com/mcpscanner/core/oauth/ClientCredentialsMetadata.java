package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.Set;

public record ClientCredentialsMetadata(URI issuer, URI tokenEndpoint, Set<String> grantTypes,
        Set<String> authenticationMethods, Set<String> signingAlgorithms) {
    public ClientCredentialsMetadata {
        grantTypes = Set.copyOf(grantTypes);
        authenticationMethods = Set.copyOf(authenticationMethods);
        signingAlgorithms = Set.copyOf(signingAlgorithms);
    }
    @Override public String toString() { return "ClientCredentialsMetadata[redacted]"; }
}
