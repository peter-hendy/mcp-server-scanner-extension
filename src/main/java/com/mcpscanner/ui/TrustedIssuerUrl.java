package com.mcpscanner.ui;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

final class TrustedIssuerUrl {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private TrustedIssuerUrl() {
    }

    static Optional<URI> parse(String text) {
        try {
            URI issuer = URI.create(text.trim());
            return isSecure(issuer) && isClean(issuer) ? Optional.of(issuer) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static boolean isSecure(URI issuer) {
        return "https".equalsIgnoreCase(issuer.getScheme())
                || "http".equalsIgnoreCase(issuer.getScheme()) && LOOPBACK_HOSTS.contains(issuer.getHost());
    }

    private static boolean isClean(URI issuer) {
        return issuer.isAbsolute() && issuer.getHost() != null && issuer.getUserInfo() == null
                && issuer.getRawQuery() == null && issuer.getRawFragment() == null;
    }
}
