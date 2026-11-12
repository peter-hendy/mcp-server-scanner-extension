package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.http.BareRequests;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class ProtectedResourceLocator {

    private static final int UNAUTHORIZED = 401;
    private static final String ANONYMOUS_PING = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

    private final OAuthHttp http;

    public ProtectedResourceLocator(OAuthHttp http) {
        this.http = http;
    }

    public Optional<ProtectedResource> locate(URI mcpEndpoint, BearerChallenge baseChallenge) {
        return Stream.<Supplier<List<URI>>>of(
                        () -> baseChallenge.resourceMetadata().stream().toList(),
                        () -> WellKnownLocations.protectedResourceMetadata(mcpEndpoint),
                        () -> anonymousChallenge(mcpEndpoint).resourceMetadata().stream().toList())
                .flatMap(locations -> locations.get().stream())
                .map(this::fetch)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<ProtectedResource> fetch(URI location) {
        return http.getJsonDocument(location).flatMap(ProtectedResourceLocator::parse);
    }

    private BearerChallenge anonymousChallenge(URI mcpEndpoint) {
        HttpRequest ping = BareRequests.fromUrl(mcpEndpoint.toString())
                .withMethod("POST")
                .withAddedHeader("Content-Type", "application/json")
                .withAddedHeader("Accept", "application/json, text/event-stream")
                .withBody(ANONYMOUS_PING);
        return http.send(ping)
                .map(exchange -> exchange.response())
                .filter(response -> response.statusCode() == UNAUTHORIZED)
                .map(BearerChallenge::of)
                .orElseGet(BearerChallenge::none);
    }

    private static Optional<ProtectedResource> parse(JsonDocument document) {
        List<String> scopes = document.texts("scopes_supported");
        Optional<String> scope = scopes.isEmpty() ? Optional.empty() : Optional.of(String.join(" ", scopes));
        return document.texts("authorization_servers").stream()
                .map(HttpUris::parse)
                .flatMap(Optional::stream)
                .findFirst()
                .map(server -> new ProtectedResource(document.webUri("resource"), server, scope));
    }

}
