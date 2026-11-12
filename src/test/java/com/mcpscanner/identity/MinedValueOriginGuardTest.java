package com.mcpscanner.identity;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MinedValueOriginGuardTest {

    private static final String ENDPOINT = "https://mcp.example.com/mcp";
    private static final MinedSource SOURCE = new MinedSource("tools/call list_documents", "documents[0].doc_id");

    private final McpEventLog eventLog = mock(McpEventLog.class);
    private IdentityRegistry registry;
    private RegistryOwnedValues store;
    private Identity alice;
    private String currentEndpoint = ENDPOINT;
    private MinedValueOriginGuard guard;

    @BeforeEach
    void setUp() {
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        store = new RegistryOwnedValues(registry);
        alice = registry.primaryUser().withName("alice");
        registry.update(alice);
        guard = MinedValueOriginGuard.watching(registry, eventLog, () -> currentEndpoint);
    }

    @Test
    void committingAnotherOriginClearsMinedValuesAndKeepsEnteredOnesWithOneInfoLine() {
        store.replaceMined(alice.id(), List.of(mined("d-1")));
        store.replaceEntered(alice.id(), List.of(new EnteredValue("doc_id", "typed")));

        guard.commit("https://other.example.com/mcp");

        assertThat(store.valuesOf(alice.id()).mined()).isEmpty();
        assertThat(store.valuesOf(alice.id()).entered()).hasSize(1);
        verify(eventLog).info("Endpoint origin changed from https://mcp.example.com to "
                + "https://other.example.com: cleared the collected IDs of alice");
    }

    @Test
    void sameOriginPathEditsKeepMinedValues() {
        store.replaceMined(alice.id(), List.of(mined("d-1")));

        guard.commit("https://mcp.example.com/other?tenant=a");
        guard.commit("");

        assertThat(store.valuesOf(alice.id()).mined()).hasSize(1);
        verify(eventLog, never()).info(anyString());
    }

    @Test
    void valuesMinedAfterAClearAreBoundToTheNewOrigin() {
        store.replaceMined(alice.id(), List.of(mined("d-1")));
        guard.commit("https://other.example.com/mcp");
        currentEndpoint = "https://other.example.com/mcp";

        store.replaceMined(alice.id(), List.of(mined("d-2")));
        guard.commit("https://other.example.com/mcp");

        assertThat(store.valuesOf(alice.id()).mined()).extracting(MinedValue::value).containsExactly("d-2");
    }

    @Test
    void minedValuesLoadedWithTheProjectAreBoundToTheEndpointSavedWithIt() {
        store.replaceMined(alice.id(), List.of(mined("d-1")));
        MinedValueOriginGuard reloaded = MinedValueOriginGuard.watching(registry, eventLog, () -> ENDPOINT);

        reloaded.commit(ENDPOINT);

        assertThat(store.valuesOf(alice.id()).mined()).hasSize(1);
    }

    @Test
    void aResourceUriQueryIsNeverKeptInTheOperation() {
        MinedSource source = new MinedSource("resources/read notes://alice/private.md?token=s3cret#top", "text");

        assertThat(source.operation()).isEqualTo("resources/read notes://alice/private.md");
        assertThat(new MinedSource("tools/call list_documents", "x").operation())
                .isEqualTo("tools/call list_documents");
    }

    private static MinedValue mined(String value) {
        return new MinedValue("doc_id", value, SOURCE, ValueScope.PRIVATE);
    }
}
