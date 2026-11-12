package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

final class ScopeSelection {

    private static final Set<String> IDENTITY_SCOPES =
            Set.of("openid", "profile", "email", "address", "phone", "offline_access");

    Optional<String> extraScope(Set<String> supported, Set<String> granted) {
        return new TreeSet<>(supported).stream()
                .filter(scope -> !granted.contains(scope))
                .filter(scope -> !IDENTITY_SCOPES.contains(scope))
                .findFirst();
    }
}
