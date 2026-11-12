package com.mcpscanner.auth.oauth.discovery;

import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;

import java.net.URI;
import java.util.List;

public record DiscoveredMetadata(URI issuer, DiscoverySource source, AuthorizationServerMetadata asMetadata,
                                 List<String> selectedScopes, ProtectedResourceDocument protectedResource,
                                 String wwwAuthenticateChallenge, MetadataFetchFailures fetchFailures) {

    public DiscoveredMetadata {
        selectedScopes = List.copyOf(selectedScopes);
        fetchFailures = fetchFailures != null ? fetchFailures : MetadataFetchFailures.none();
    }

    public DiscoveredMetadata(URI issuer, DiscoverySource source, AuthorizationServerMetadata asMetadata,
                              List<String> selectedScopes, ProtectedResourceDocument protectedResource,
                              String wwwAuthenticateChallenge) {
        this(issuer, source, asMetadata, selectedScopes, protectedResource, wwwAuthenticateChallenge,
                MetadataFetchFailures.none());
    }

    public DiscoveredMetadata(URI issuer, DiscoverySource source, AuthorizationServerMetadata asMetadata,
                              List<String> selectedScopes) {
        this(issuer, source, asMetadata, selectedScopes, null, null);
    }

    public DiscoveredMetadata(URI issuer, DiscoverySource source, AuthorizationServerMetadata asMetadata) {
        this(issuer, source, asMetadata, List.of());
    }

    public DiscoveredMetadata withScopes(List<String> scopes) {
        return new DiscoveredMetadata(issuer, source, asMetadata, scopes, protectedResource,
                wwwAuthenticateChallenge, fetchFailures);
    }

    public DiscoveredMetadata withWwwAuthenticateChallenge(String challenge) {
        return new DiscoveredMetadata(issuer, source, asMetadata, selectedScopes, protectedResource,
                challenge, fetchFailures);
    }

    public DiscoveredMetadata withFetchFailures(MetadataFetchFailures failures) {
        return new DiscoveredMetadata(issuer, source, asMetadata, selectedScopes, protectedResource,
                wwwAuthenticateChallenge, failures);
    }
}
