package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CredentialOriginGuardTest {

    private static final String ENDPOINT = "https://mcp.example.com/mcp";

    private final McpEventLog eventLog = mock(McpEventLog.class);
    private IdentityRegistry registry;
    private Identity alice;
    private Identity bob;
    private CredentialOriginGuard guard;
    private String currentEndpoint = ENDPOINT;

    @BeforeEach
    void setUp() {
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        alice = registry.add(Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER));
        bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-token"));
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));
        guard = CredentialOriginGuard.watching(registry, eventLog, () -> currentEndpoint);
    }

    @Test
    void retypingTheSameEndpointKeepsCredentials() {
        assertThat(guard.commit(ENDPOINT)).isEmpty();

        assertCredentialsKept();
    }

    @Test
    void editingOnlyThePathKeepsCredentials() {
        assertThat(guard.commit("https://mcp.example.com/mcpx")).isEmpty();
        assertThat(guard.commit(ENDPOINT + "?tenant=a")).isEmpty();

        assertCredentialsKept();
    }

    @Test
    void anEmptyOrUnparseableEndpointKeepsCredentialsAndTheBoundOrigin() {
        assertThat(guard.commit("")).isEmpty();
        assertThat(guard.commit("https://")).isEmpty();
        assertThat(guard.commit(ENDPOINT)).isEmpty();

        assertCredentialsKept();
    }

    @Test
    void spellingTheSameOriginDifferentlyKeepsCredentials() {
        assertThat(guard.commit("HTTPS://MCP.Example.com:443/other")).isEmpty();

        assertCredentialsKept();
    }

    @Test
    void anotherHostClearsCredentialsAndNamesTheOriginsAndUsers() {
        CredentialsCleared cleared = guard.commit("https://other.example.test/mcp").orElseThrow();

        assertThat(registry.hasAnyCredential()).isFalse();
        assertThat(cleared.message()).isEqualTo("Endpoint origin changed from https://mcp.example.com to "
                + "https://other.example.test: cleared the credentials of alice, bob. Enter them again in Users.");
        verify(eventLog).info(cleared.message());
    }

    @Test
    void anotherPortOrSchemeIsAnotherOrigin() {
        assertThat(guard.commit("https://mcp.example.com:8443/mcp")).isPresent();
        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-token"));

        assertThat(guard.commit("http://mcp.example.com:8443/mcp")).isPresent();
        assertThat(registry.hasAnyCredential()).isFalse();
    }

    @Test
    void credentialsEnteredAfterTheOriginChangeBelongToTheNewOrigin() {
        currentEndpoint = "https://other.example.test/mcp";
        guard.commit(currentEndpoint);
        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-for-other"));

        assertThat(guard.commit("https://other.example.test/mcp/v2")).isEmpty();
        assertThat(registry.credential(alice.id())).isPresent();
    }

    @Test
    void credentialsReenteredForTheNewEndpointBeforeItIsCommittedAreKept() {
        currentEndpoint = "https://other.example.test/mcp";
        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-for-other"));
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-for-other"));

        assertThat(guard.commit(currentEndpoint)).isEmpty();
        assertCredentialsKept();
        verify(eventLog, never()).info(anyString());
    }

    @Test
    void onlyCredentialsEnteredForAnotherOriginAreClearedAndNamed() {
        currentEndpoint = "https://other.example.test/mcp";
        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-for-other"));

        CredentialsCleared cleared = guard.commit(currentEndpoint).orElseThrow();

        assertThat(registry.credential(alice.id())).isPresent();
        assertThat(registry.credential(bob.id())).isEmpty();
        assertThat(cleared.message()).isEqualTo("Endpoint origin changed from https://mcp.example.com to "
                + "https://other.example.test: cleared the credentials of bob. Enter them again in Users.");
    }

    @Test
    void credentialsFromSeveralOtherOriginsNameEachOrigin() {
        currentEndpoint = "https://third.example.test/mcp";
        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-for-third"));

        CredentialsCleared cleared = guard.commit("https://other.example.test/mcp").orElseThrow();

        assertThat(registry.hasAnyCredential()).isFalse();
        assertThat(cleared.message()).isEqualTo("Endpoint origin changed from https://third.example.test and "
                + "https://mcp.example.com to https://other.example.test: cleared the credentials of alice, bob. "
                + "Enter them again in Users.");
    }

    @Test
    void changingTheOriginWithNoCredentialsHeldReportsNothing() {
        registry.clearCredentials();

        assertThat(guard.commit("https://other.example.test/mcp")).isEmpty();
        verify(eventLog, never()).info(anyString());
    }

    @Test
    void credentialsEnteredBeforeAnyEndpointBindToTheFirstCommittedOrigin() {
        registry.clearCredentials();
        currentEndpoint = "";
        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-token"));
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));

        assertThat(guard.commit(ENDPOINT)).isEmpty();
        assertCredentialsKept();
        assertThat(guard.commit("https://other.example.test/mcp")).isPresent();
    }

    @Test
    void credentialsHeldBeforeTheGuardStartsBindToTheEndpointCurrentThen() {
        CredentialOriginGuard late = CredentialOriginGuard.watching(registry, eventLog, () -> "https://late.example.test/");

        assertThat(late.commit("https://late.example.test/mcp")).isEmpty();
        assertCredentialsKept();
    }

    private void assertCredentialsKept() {
        assertThat(registry.credential(alice.id())).isPresent();
        assertThat(registry.credential(bob.id())).isPresent();
    }
}
