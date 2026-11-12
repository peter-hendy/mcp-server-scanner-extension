package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public record RedirectTrail(List<HttpRequestResponse> exchanges, URI lastRequested, Optional<URI> stoppedAt,
                           boolean hopLimitReached) {

    private static final String COOKIE = "Cookie";

    public RedirectTrail {
        exchanges = List.copyOf(exchanges);
    }

    public int redirectsFollowed() {
        return exchanges.size() - 1;
    }

    public Optional<URI> sessionCreatedBy() {
        if (exchanges.stream().noneMatch(exchange -> exchange.request().hasHeader(COOKIE))) {
            return Optional.empty();
        }
        return exchanges.stream()
                .filter(exchange -> !ResponseHeaders.values(exchange.response(), "Set-Cookie").isEmpty())
                .findFirst()
                .flatMap(exchange -> HttpUris.parse(exchange.request().url()))
                .map(url -> URI.create(HttpUris.withoutQuery(url)));
    }
}
