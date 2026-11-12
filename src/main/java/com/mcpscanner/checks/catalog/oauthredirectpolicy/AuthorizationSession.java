package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.shared.oauth.HttpUris;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

final class AuthorizationSession {

    private static final String COOKIE = "Cookie";

    private AuthorizationSession() {
    }

    static Map<String, String> headersFor(URI authorizationEndpoint, URI mcpEndpoint,
                                          String baseCookieHeader, Supplier<Map<String, String>> authHeaders) {
        if (!HttpUris.sameOrigin(authorizationEndpoint, mcpEndpoint)) {
            return Map.of();
        }
        Set<String> cookies = new LinkedHashSet<>();
        addCookies(cookies, baseCookieHeader);
        authHeaders.get().entrySet().stream()
                .filter(header -> COOKIE.equalsIgnoreCase(header.getKey()))
                .forEach(header -> addCookies(cookies, header.getValue()));
        return cookies.isEmpty() ? Map.of() : Map.of(COOKIE, String.join("; ", cookies));
    }

    private static void addCookies(Set<String> cookies, String header) {
        Optional.ofNullable(header).stream()
                .flatMap(value -> Arrays.stream(value.split(";")))
                .map(String::trim)
                .filter(cookie -> cookie.contains("="))
                .forEach(cookies::add);
    }
}
