package com.mcpscanner.checks.shared.oauth;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

public final class AuthorizationServerMetadataFetcher {

    private final OAuthHttp http;

    public AuthorizationServerMetadataFetcher(OAuthHttp http) {
        this.http = http;
    }

    public <T> Optional<T> fetch(URI issuer, Function<JsonDocument, Optional<T>> reader) {
        return WellKnownLocations.authorizationServerMetadata(issuer).stream()
                .map(http::getJsonDocument)
                .flatMap(Optional::stream)
                .map(reader)
                .flatMap(Optional::stream)
                .findFirst();
    }
}
