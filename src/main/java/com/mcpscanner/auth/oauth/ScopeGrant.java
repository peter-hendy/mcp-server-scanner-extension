package com.mcpscanner.auth.oauth;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public record ScopeGrant(Set<String> requested, Set<String> granted) {

    public ScopeGrant {
        requested = Set.copyOf(requested);
        granted = Set.copyOf(granted);
    }

    public Set<String> withheld() {
        Set<String> withheld = new TreeSet<>(requested);
        withheld.removeAll(granted);
        return withheld;
    }

    public Optional<String> withheldWarning() {
        Set<String> withheld = withheld();
        if (withheld.isEmpty()) {
            return Optional.empty();
        }
        String missing = describe(withheld);
        return Optional.of("Authorization server granted " + describe(granted) + " but withheld " + missing
                + "; tools needing " + missing + " will fail. Reconnect as a user allowed " + missing
                + " to test them.");
    }

    static String describe(Collection<String> scopes) {
        return scopes.isEmpty() ? "no scopes" : String.join(" ", new TreeSet<>(scopes));
    }
}
