package com.mcpscanner.auth.grants;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRegistryTest {

    private record Named(String id, String value) {}

    private static final ProviderRegistry.Rules<Named> RULES = new ProviderRegistry.Rules<>(
            Named::id, named -> named.value() != null,
            () -> new IllegalStateException("invalid"),
            () -> new IllegalStateException("load"),
            () -> new IllegalStateException("missing"));

    @Test
    void requiresARegisteredIdAndListsIdsSorted() {
        var registry = new ProviderRegistry<>(List.of(new Named("b", "2"), new Named("a", "1")), RULES);

        assertThat(registry.ids()).containsExactly("a", "b");
        assertThat(registry.require("a").value()).isEqualTo("1");
        assertThatThrownBy(() -> registry.require("c")).hasMessage("missing");
        assertThatThrownBy(() -> registry.require(null)).hasMessage("missing");
    }

    @Test
    void duplicateBlankNullOrIncompleteRegistrationsFailClosed() {
        for (List<Named> registrations : List.of(
                List.of(new Named("a", "1"), new Named("a", "2")),
                List.of(new Named(" ", "1")),
                List.of(new Named("a", null)),
                Arrays.asList((Named) null))) {
            assertThatThrownBy(() -> new ProviderRegistry<>(registrations, RULES)).hasMessage("invalid");
        }
    }

    @Test
    void serviceLoaderFailureSurfacesTheConfiguredLoadFailure() {
        ClassLoader broken = new ClassLoader(null) {
            @Override
            public java.util.Enumeration<java.net.URL> getResources(String name) {
                throw new IllegalStateException("secret detail");
            }
        };

        assertThatThrownBy(() -> ProviderRegistry.load(Named.class, broken, RULES)).hasMessage("load");
    }
}
