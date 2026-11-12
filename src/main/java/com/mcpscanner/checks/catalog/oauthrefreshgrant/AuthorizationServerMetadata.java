package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

record AuthorizationServerMetadata(URI tokenEndpoint, Set<String> scopesSupported, Set<String> authMethodsSupported) {

    private static final String PUBLIC_CLIENT_METHOD = "none";

    AuthorizationServerMetadata {
        Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
        scopesSupported = Set.copyOf(scopesSupported);
        authMethodsSupported = Set.copyOf(authMethodsSupported);
    }

    boolean admitsOnlyConfidentialClients() {
        return !authMethodsSupported.isEmpty() && !authMethodsSupported.contains(PUBLIC_CLIENT_METHOD);
    }
}
