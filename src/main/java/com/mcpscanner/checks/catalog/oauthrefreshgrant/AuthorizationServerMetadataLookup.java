package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.checks.shared.oauth.AuthorizationServerMetadataFetcher;
import com.mcpscanner.checks.shared.oauth.JsonDocument;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

final class AuthorizationServerMetadataLookup {

    private final AuthorizationServerMetadataFetcher fetcher;

    AuthorizationServerMetadataLookup(OAuthHttp http) {
        this.fetcher = new AuthorizationServerMetadataFetcher(http);
    }

    Optional<AuthorizationServerMetadata> lookup(URI issuer) {
        return fetcher.fetch(issuer, document -> read(document, issuer));
    }

    static Optional<AuthorizationServerMetadata> read(JsonDocument document, URI expectedIssuer) {
        if (!issuedBy(document, expectedIssuer)) {
            return Optional.empty();
        }
        return document.webUri("token_endpoint").map(tokenEndpoint -> new AuthorizationServerMetadata(tokenEndpoint,
                Set.copyOf(document.texts("scopes_supported")),
                Set.copyOf(document.texts("token_endpoint_auth_methods_supported"))));
    }

    private static boolean issuedBy(JsonDocument document, URI expectedIssuer) {
        return document.text("issuer")
                .map(issuer -> withoutTrailingSlash(issuer).equals(withoutTrailingSlash(expectedIssuer.toString())))
                .orElse(false);
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
