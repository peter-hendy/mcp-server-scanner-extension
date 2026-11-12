package com.mcpscanner.checks.catalog.jwtvalidation;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

final class BearerJwtLocator {

    private final Supplier<AuthStrategy> authStrategy;

    BearerJwtLocator(Supplier<AuthStrategy> authStrategy) {
        this.authStrategy = authStrategy;
    }

    Optional<String> locate(HttpRequest request) {
        return bearerJwt(request.headerValue(BearerPlacement.AUTHORIZATION)).or(this::strategyBearerJwt);
    }

    private Optional<String> strategyBearerJwt() {
        AuthStrategy strategy = authStrategy.get();
        if (strategy == null) {
            return Optional.empty();
        }
        return strategy.headers().entrySet().stream()
                .filter(header -> BearerPlacement.AUTHORIZATION.equalsIgnoreCase(header.getKey()))
                .map(Map.Entry::getValue)
                .map(BearerJwtLocator::bearerJwt)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> bearerJwt(String authorization) {
        String prefix = BearerPlacement.BEARER_PREFIX;
        if (authorization == null || !authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return Optional.empty();
        }
        String token = authorization.substring(prefix.length()).trim();
        return CompactJwt.parse(token).isPresent() ? Optional.of(token) : Optional.empty();
    }
}
