package com.mcpscanner.access;

import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.config.ExtensionConfigStore;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public final class PersistedAccessExpectations implements AccessExpectations {

    private final ExtensionConfigStore store;
    private final Supplier<String> endpoint;

    public PersistedAccessExpectations(ExtensionConfigStore store, Supplier<String> endpoint) {
        this.store = store;
        this.endpoint = endpoint;
    }

    @Override
    public Optional<AccessExpectation> stored(AccessItemKey item) {
        String stored = store.accessExpectation(endpoint.get(), storageName(item));
        return Arrays.stream(AccessExpectation.values()).filter(value -> value.name().equals(stored)).findFirst();
    }

    @Override
    public void store(AccessItemKey item, AccessExpectation expectation) {
        store.setAccessExpectation(endpoint.get(), storageName(item), expectation.name());
    }

    private static String storageName(AccessItemKey item) {
        return item.kind().name().toLowerCase(Locale.ROOT) + "." + item.name();
    }
}
