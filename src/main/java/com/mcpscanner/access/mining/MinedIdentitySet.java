package com.mcpscanner.access.mining;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

final class MinedIdentitySet {

    private final AtomicReference<Optional<Set<String>>> mined = new AtomicReference<>(Optional.empty());

    void record(Set<String> identityIds) {
        mined.set(Optional.of(Set.copyOf(identityIds)));
    }

    boolean matches(Set<String> identityIds) {
        return mined.get().filter(identityIds::equals).isPresent();
    }

    void forget() {
        mined.set(Optional.empty());
    }
}
