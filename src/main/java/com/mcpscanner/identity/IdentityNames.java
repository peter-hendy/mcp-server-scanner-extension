package com.mcpscanner.identity;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class IdentityNames {

    private final Set<String> taken;

    private IdentityNames(Set<String> taken) {
        this.taken = taken;
    }

    public static IdentityNames of(Collection<Identity> identities) {
        return new IdentityNames(identities.stream().map(Identity::name).map(IdentityNames::normalised)
                .collect(Collectors.toUnmodifiableSet()));
    }

    public boolean isTaken(String name) {
        return taken.contains(normalised(name));
    }

    public String unused(String base) {
        return isTaken(base) ? firstFreeNumbered(base, 2) : base;
    }

    public String unusedNumbered(String base) {
        return firstFreeNumbered(base, 1);
    }

    private String firstFreeNumbered(String base, int first) {
        int number = first;
        while (isTaken(base + " " + number)) {
            number++;
        }
        return base + " " + number;
    }

    private static String normalised(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
