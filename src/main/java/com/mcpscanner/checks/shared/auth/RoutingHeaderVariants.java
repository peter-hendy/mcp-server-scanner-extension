package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.List;

public final class RoutingHeaderVariants implements AuthVariantGenerator {

    private static final String MIDDLEWARE = repeated("middleware");
    private static final String MIDDLEWARE_CONTROL = repeated("neutral");
    private static final String MIDDLEWARE_SRC = repeated("src/middleware");
    private static final String MIDDLEWARE_SRC_CONTROL = repeated("neutral/middleware");

    private final String canary;

    public RoutingHeaderVariants(String canary) {
        this.canary = canary;
    }

    @Override
    public List<AuthVariant> variantsFor(HttpRequest seed) {
        List<HeaderProfile> profiles = new ArrayList<>(pathHeaderProfiles(seed));
        profiles.addAll(ClientAddressVariants.profiles());
        profiles.addAll(middlewareProfiles());
        profiles.addAll(protocolProfiles());
        return profiles.stream()
                .filter(HeaderProfile::safeAscii)
                .map(profile -> profile.addedTo(VariantFamily.ROUTING_HEADER))
                .toList();
    }

    private List<HeaderProfile> pathHeaderProfiles(HttpRequest seed) {
        String control = new ProbeTargets(seed, canary).nonexistentChildTarget();
        return List.of(
                new HeaderProfile("routing.x-original-url", "X-Original-URL", seed.path(), control),
                new HeaderProfile("routing.x-original-uri", "X-Original-URI", seed.path(), control),
                new HeaderProfile("routing.x-rewrite-url", "X-Rewrite-URL", seed.path(), control),
                new HeaderProfile("routing.x-forwarded-uri", "X-Forwarded-Uri", "/", control),
                new HeaderProfile("routing.x-forwarded-prefix", "X-Forwarded-Prefix", "/", control));
    }

    private static List<HeaderProfile> middlewareProfiles() {
        return List.of(
                new HeaderProfile("middleware", "X-Middleware-Subrequest", MIDDLEWARE, MIDDLEWARE_CONTROL),
                new HeaderProfile("middleware-src", "X-Middleware-Subrequest", MIDDLEWARE_SRC,
                        MIDDLEWARE_SRC_CONTROL));
    }

    private static List<HeaderProfile> protocolProfiles() {
        return List.of(
                new HeaderProfile("proto.x-forwarded-proto", "X-Forwarded-Proto", "https", "ftp"),
                new HeaderProfile("proto.x-forwarded-proto-alias", "X_Forwarded_Proto", "https", "ftp"));
    }

    private static String repeated(String segment) {
        return String.join(":", segment, segment, segment, segment, segment);
    }
}
