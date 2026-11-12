package com.mcpscanner.identity;

import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RegistryOwnedValuesTest {

    private static final MinedSource SOURCE = new MinedSource("tools/call list_documents", "items[0].doc_id");

    private ExtensionConfigStore config;
    private IdentityRegistry registry;
    private OwnedValueStore store;
    private Identity alice;
    private Identity bob;

    @BeforeEach
    void setUp() {
        config = InMemoryPersistedObject.configStore();
        registry = new IdentityRegistry(identityStore());
        store = new RegistryOwnedValues(registry);
        alice = registry.primaryUser();
        registry.update(alice.withOwnedValues(OwnedValues.entered(Map.of("note_id", "typed"))));
        bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
    }

    @Test
    void replacingMinedValuesKeepsEnteredOnesAndPersists() {
        store.replaceMined(alice.id(), List.of(mined("doc_id", "d-1")));
        store.replaceMined(alice.id(), List.of(mined("doc_id", "d-2")));

        OwnedValues reloaded = new IdentityRegistry(identityStore()).require(alice.id()).ownedValues();
        assertThat(reloaded.entered()).containsExactly(new EnteredValue("note_id", "typed"));
        assertThat(reloaded.mined()).containsExactly(mined("doc_id", "d-2"));
        assertThat(store.valuesOf(alice.id())).isEqualTo(reloaded);
    }

    @Test
    void clearingOneUserLeavesTheOthersMinedValues() {
        store.replaceMined(alice.id(), List.of(mined("doc_id", "d-1")));
        store.replaceMined(bob.id(), List.of(mined("doc_id", "d-9")));

        store.clearMined(alice.id());

        assertThat(store.valuesOf(alice.id()).all()).containsExactly(new EnteredValue("note_id", "typed"));
        assertThat(store.valuesOf(bob.id()).mined()).containsExactly(mined("doc_id", "d-9"));
    }

    @Test
    void clearingAllUsersKeepsEnteredValuesAndNotifiesOnce() {
        store.replaceMined(alice.id(), List.of(mined("doc_id", "d-1")));
        store.replaceMined(bob.id(), List.of(mined("doc_id", "d-9")));
        AtomicInteger changes = new AtomicInteger();
        store.addListener(changes::incrementAndGet);

        store.clearAllMined();

        assertThat(store.valuesOf(alice.id()).all()).containsExactly(new EnteredValue("note_id", "typed"));
        assertThat(store.valuesOf(bob.id()).isEmpty()).isTrue();
        assertThat(new IdentityRegistry(identityStore()).require(bob.id()).ownedValues().isEmpty()).isTrue();
        assertThat(changes).hasValue(1);
    }

    @Test
    void replacingEnteredValuesKeepsMinedOnes() {
        store.replaceMined(alice.id(), List.of(mined("doc_id", "d-1")));

        store.replaceEntered(alice.id(), List.of(new EnteredValue("note_id", "retyped")));

        assertThat(store.valuesOf(alice.id()).all())
                .containsExactly(new EnteredValue("note_id", "retyped"), mined("doc_id", "d-1"));
    }

    @Test
    void listenersHearEveryChangeAndCanBeRemoved() {
        AtomicInteger changes = new AtomicInteger();
        Runnable listener = changes::incrementAndGet;
        store.addListener(listener);

        store.replaceMined(alice.id(), List.of(mined("doc_id", "d-1")));
        store.clearMined(alice.id());
        store.removeListener(listener);
        store.replaceMined(alice.id(), List.of(mined("doc_id", "d-2")));

        assertThat(changes).hasValue(2);
    }

    @Test
    void anUnknownUserIsRefused() {
        assertThatThrownBy(() -> store.replaceMined("missing", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private IdentityStore identityStore() {
        return new IdentityStore(config, mock(McpEventLog.class));
    }

    private static MinedValue mined(String argument, String value) {
        return new MinedValue(argument, value, SOURCE, ValueScope.PRIVATE);
    }
}
