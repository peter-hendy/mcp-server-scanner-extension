package com.mcpscanner.ui.connect;

import java.net.URI;

public final class EndpointUrls {

    private EndpointUrls() {
    }

    public static boolean hasHttpScheme(String url) {
        String scheme = parse(url).map(URI::getScheme).orElse(null);
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    public static String hostOr(String url, String fallback) {
        return parse(url).map(URI::getHost).orElse(fallback);
    }

    private static java.util.Optional<URI> parse(String url) {
        try {
            return java.util.Optional.of(URI.create(url));
        } catch (IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }
}
