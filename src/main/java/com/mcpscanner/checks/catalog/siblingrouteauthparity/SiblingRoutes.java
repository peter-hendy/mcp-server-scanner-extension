package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.AnonymousVariants;
import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.auth.CredentialStripper;
import com.mcpscanner.checks.shared.auth.RequestMutation;
import com.mcpscanner.checks.shared.auth.SiblingRouteVariants;
import com.mcpscanner.checks.shared.auth.VariantFamily;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class SiblingRoutes {

    private final Supplier<Optional<HttpService>> sseProbeService;
    private final AuthVariantSuite suite;
    private final AdvertisedMessageRoute advertised;
    private final AnonymousVariants known;

    SiblingRoutes(Supplier<Optional<HttpService>> sseProbeService, AuthVariantSuite suite,
                  CredentialStripper stripper) {
        this.sseProbeService = sseProbeService;
        this.suite = suite;
        this.advertised = new AdvertisedMessageRoute(stripper);
        this.known = new AnonymousVariants(new SiblingRouteVariants(), stripper);
    }

    List<SiblingRoute> anonymousRoutesFor(HttpRequest seed) {
        boolean sse = sseProbeService.get().isPresent();
        List<SiblingRoute> routes = new ArrayList<>();
        if (sse) {
            routes.add(advertised.routeFor(seed));
        }
        Map<String, RequestMutation> sweptElsewhere = sse ? Map.of() : controlledPathVariants(seed);
        known.variantsFor(seed).stream()
                .map(variant -> new SiblingRoute(variant, FindingKind.KNOWN_SIBLING,
                        Optional.ofNullable(sweptElsewhere.get(variant.candidate().value()))))
                .forEach(routes::add);
        return List.copyOf(routes);
    }

    private Map<String, RequestMutation> controlledPathVariants(HttpRequest seed) {
        return suite.variantsFor(seed, Set.of(VariantFamily.PATH)).stream()
                .filter(variant -> variant.control().isPresent())
                .collect(Collectors.toMap(variant -> variant.candidate().value(),
                        variant -> variant.control().orElseThrow(), (first, second) -> first));
    }
}
