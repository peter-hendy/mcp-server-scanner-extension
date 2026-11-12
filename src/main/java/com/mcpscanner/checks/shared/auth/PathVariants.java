package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PathVariants implements AuthVariantGenerator {

    private final String canary;

    public PathVariants(String canary) {
        this.canary = canary;
    }

    @Override
    public List<AuthVariant> variantsFor(HttpRequest seed) {
        ProbeTargets targets = new ProbeTargets(seed, canary);
        Set<String> attempted = new HashSet<>(Set.of(seed.path()));
        List<AuthVariant> variants = new ArrayList<>();
        for (PathShape shape : PathShape.values()) {
            String candidate = targets.target(shape.applyTo(targets.endpointPath()));
            if (attempted.add(candidate)) {
                variants.add(variant(shape, candidate,
                        targets.target(shape.applyTo(targets.nonexistentChildPath()))));
            }
        }
        return List.copyOf(variants);
    }

    private static AuthVariant variant(PathShape shape, String candidate, String control) {
        return AuthVariant.controlled(VariantFamily.PATH, "path." + shape.label(),
                new RequestMutation(candidate, request -> request.withPath(candidate)),
                new RequestMutation(control, request -> request.withPath(control)));
    }
}
