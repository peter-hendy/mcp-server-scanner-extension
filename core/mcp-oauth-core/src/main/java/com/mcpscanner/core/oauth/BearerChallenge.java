package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.Set;

public record BearerChallenge(URI resourceMetadata, String error, Set<String> scopes, boolean scopeSpecified) {
    public BearerChallenge {
        scopes = Set.copyOf(scopes);
        if (scopes.stream().anyMatch(scope -> !ScopeValues.valid(scope)) || !scopeSpecified && !scopes.isEmpty()
                || resourceMetadata != null && !OAuthDiscoveryPolicy.validLocation(resourceMetadata))
            throw new OAuthPolicyException(OAuthPolicyException.Reason.INVALID_CHALLENGE);
    }
    @Override public String toString() { return "BearerChallenge[redacted]"; }
}
