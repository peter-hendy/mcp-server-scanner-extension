package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SiblingRouteVariants implements AuthVariantGenerator {

    private static final List<String> KNOWN_ROUTE_NAMES =
            List.of("mcp_message", "messages", "messages/", "message", "sse", "mcp", "mcp/");

    @Override
    public List<AuthVariant> variantsFor(HttpRequest seed) {
        ProbeTargets targets = new ProbeTargets(seed);
        String endpoint = targets.endpointPath();
        return siblingPaths(endpoint).stream()
                .filter(path -> !path.equals(endpoint))
                .map(path -> variant(path, targets.target(path)))
                .toList();
    }

    private static Set<String> siblingPaths(String endpoint) {
        Set<String> paths = new LinkedHashSet<>();
        paths.add(PathText.messageSibling(endpoint));
        paths.add(PathText.withTrailingSlashAddedOrRemoved(endpoint));
        for (String directory : new LinkedHashSet<>(List.of(PathText.directoryOf(endpoint), "/"))) {
            KNOWN_ROUTE_NAMES.forEach(name -> paths.add(directory + name));
        }
        return paths;
    }

    private static AuthVariant variant(String path, String target) {
        return AuthVariant.uncontrolled(VariantFamily.PATH, "sibling." + path,
                new RequestMutation(target, request -> request.withPath(target)));
    }
}
