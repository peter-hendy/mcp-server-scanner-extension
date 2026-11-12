package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.Set;

public record EnterpriseAuthorizationMetadata(URI issuer, URI tokenEndpoint, Set<String> grantProfiles,
        Set<String> authenticationMethods, Set<String> signingAlgorithms) {
    public EnterpriseAuthorizationMetadata {
        grantProfiles = Set.copyOf(grantProfiles);
        authenticationMethods = Set.copyOf(authenticationMethods);
        signingAlgorithms = Set.copyOf(signingAlgorithms);
    }
    @Override public String toString() { return "EnterpriseAuthorizationMetadata[redacted]"; }
}
