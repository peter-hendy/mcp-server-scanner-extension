package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

final class ProbeTargets {

    private final HttpRequest seed;
    private final String canary;

    ProbeTargets(HttpRequest seed) {
        this(seed, "");
    }

    ProbeTargets(HttpRequest seed, String canary) {
        this.seed = seed;
        this.canary = canary;
    }

    String endpointPath() {
        return seed.pathWithoutQuery();
    }

    String nonexistentChildPath() {
        return PathText.appendSegment(endpointPath(), "/__mcp_" + canary);
    }

    String nonexistentChildTarget() {
        return target(nonexistentChildPath());
    }

    String target(String pathWithoutQuery) {
        String query = seed.query();
        return query == null || query.isEmpty() ? pathWithoutQuery : pathWithoutQuery + "?" + query;
    }
}
