package com.mcpscanner.checks.catalog.jwtvalidation;

import java.util.List;

final class ForgeryCatalog {

    private final List<ForgeryStrategy> strategies;

    ForgeryCatalog(List<ForgeryStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    static ForgeryCatalog defaultCatalog() {
        return new ForgeryCatalog(List.of(new RandomKeyForgery(), new AlgNoneForgery(),
                new PublicKeyAsHmacSecretForgery(), new EmptySecretUnknownKeyIdForgery()));
    }

    List<ForgeryAttempt> attemptsFor(ForgeryContext context) {
        return strategies.stream().flatMap(strategy -> strategy.attemptsFor(context).stream()).toList();
    }
}
