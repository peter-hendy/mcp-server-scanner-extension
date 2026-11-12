package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.oauth.discovery.OAuthWellKnownPaths;
import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.logging.McpEventLog;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.mcpscanner.core.oauth.MetadataRedirectPolicy;

import java.net.URI;
import java.util.Optional;

final class IssuerMetadataResolver {

    private static final String PURPOSE_ISSUER_METADATA = "OAuth issuer metadata";
    private static final String PURPOSE_AUTHORIZATION_ENDPOINT = "OAuth authorization endpoint";
    private static final String PURPOSE_TOKEN_ENDPOINT = "OAuth token endpoint";
    private static final String PURPOSE_REGISTRATION_ENDPOINT = "OAuth registration endpoint";

    private final BurpMetadataClient metadataClient;
    private final OAuthDestinationGuard guard;
    private final AsEndpointHostConsistency endpointHosts;
    private final McpEventLog eventLog;

    IssuerMetadataResolver(BurpMetadataClient metadataClient, OAuthDestinationGuard guard,
                           McpEventLog eventLog) {
        this.metadataClient = metadataClient;
        this.guard = guard;
        this.endpointHosts = new AsEndpointHostConsistency(eventLog);
        this.eventLog = eventLog;
    }

    AuthorizationServerMetadata resolve(URI issuer, URI mcpResource, AuthorizationServerMetadata preDiscovered) {
        ExpectedIssuer expected = requireIssuer(issuer);
        if (preDiscovered != null) {
            expected.requireDeclaredBy(preDiscovered);
            requireAllowedEndpoints(preDiscovered, mcpResource);
            return preDiscovered;
        }
        guard.requireAllowed(issuer, PURPOSE_ISSUER_METADATA, mcpResource);
        return fetchFromWellKnown(expected, mcpResource);
    }

    private static ExpectedIssuer requireIssuer(URI issuer) {
        if (issuer == null) {
            throw new OAuthException("OAuth issuer URI is required");
        }
        return new ExpectedIssuer(issuer);
    }

    private AuthorizationServerMetadata fetchFromWellKnown(ExpectedIssuer expected, URI mcpResource) {
        for (URI configUri : OAuthWellKnownPaths.authorizationServerMetadataUrls(expected.uri())) {
            Optional<AuthorizationServerMetadata> metadata = fetchValidated(configUri, expected, mcpResource);
            if (metadata.isPresent()) {
                return metadata.get();
            }
        }
        throw new OAuthException("Failed to resolve authorization server metadata: no valid document at standard discovery endpoints");
    }

    private Optional<AuthorizationServerMetadata> fetchValidated(URI configUri, ExpectedIssuer expected, URI mcpResource) {
        try {
            MetadataRedirectPolicy.Response fetched = metadataClient.fetch(configUri, destination -> guard.allows(
                    destination, FetchPurpose.of(PURPOSE_ISSUER_METADATA, mcpResource, expected.uri())));
            if (fetched.status() != 200 || fetched.body() == null) {
                return Optional.empty();
            }
            return Optional.of(validated(configUri, expected, mcpResource, fetched.body()));
        } catch (ParseException e) {
            eventLog.warn("OAuth metadata fetch failed for " + configUri + ": " + e.getMessage());
        } catch (IllegalArgumentException rejected) {
            eventLog.warn("OAuth metadata fetch rejected for " + configUri + ": " + rejected.getMessage());
        }
        return Optional.empty();
    }

    private AuthorizationServerMetadata validated(URI configUri, ExpectedIssuer expected, URI mcpResource, String body)
            throws ParseException {
        AuthorizationServerMetadata metadata = AuthorizationServerMetadata.parse(JSONObjectUtils.parse(body));
        if (!expected.matches(metadata.getIssuer().getValue())) {
            eventLog.warn("OAuth metadata at " + configUri + " declares issuer " + metadata.getIssuer().getValue()
                    + " but " + expected.uri() + " was expected");
            throw new OAuthException("OAuth metadata issuer mismatch");
        }
        requireAllowedEndpoints(metadata, mcpResource);
        endpointHosts.report(metadata, configUri);
        return metadata;
    }

    private void requireAllowedEndpoints(AuthorizationServerMetadata metadata, URI mcpResource) {
        guard.requireAllowedIfPresent(metadata.getAuthorizationEndpointURI(), PURPOSE_AUTHORIZATION_ENDPOINT, mcpResource);
        guard.requireAllowedIfPresent(metadata.getTokenEndpointURI(), PURPOSE_TOKEN_ENDPOINT, mcpResource);
        guard.requireAllowedIfPresent(metadata.getRegistrationEndpointURI(), PURPOSE_REGISTRATION_ENDPOINT, mcpResource);
    }

}
