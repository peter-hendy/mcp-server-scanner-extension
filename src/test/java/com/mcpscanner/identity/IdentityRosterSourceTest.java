package com.mcpscanner.identity;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CurrentAuthHolder;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRosterSourceTest {

    private CurrentAuthHolder primaryAuth;
    private IdentityRegistry registry;
    private IdentityRosterSource source;

    @BeforeEach
    void setUp() {
        primaryAuth = new CurrentAuthHolder();
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        source = new IdentityRosterSource(primaryAuth, registry);
    }

    @Test
    void primaryIsTheConnectAsUserCarryingTheConnectionCredential() {
        primaryAuth.set(new BearerTokenAuthStrategy("alice-token"));

        Principal primary = source.get().primary();

        assertThat(primary.id()).isEqualTo(Principal.PRIMARY_ID);
        assertThat(primary.name()).isEqualTo("User 1");
        assertThat(primary.role()).isEqualTo(PrincipalRole.PRIMARY);
        assertThat(primary.privateMarker()).isEmpty();
        assertThat(primary.credentialHeaders().get()).containsEntry("Authorization", "Bearer alice-token");
    }

    @Test
    void comparedIdentitiesBecomePrincipalsWithTheirOwnCredentialAndRole() {
        Identity carol = registry.add(Identity.create("carol", IdentityRole.LOWER, IdentityAuthKind.BEARER)
                .withPrivateMarker("carol-marker")
                .withOwnedValues(OwnedValues.entered(Map.of("doc_id", "7"))));
        registry.add(Identity.create("unsigned", IdentityRole.PEER, IdentityAuthKind.BEARER));
        registry.setCredential(carol.id(), new BearerTokenAuthStrategy("carol-token"));

        PrincipalRoster roster = source.get();

        assertThat(roster.compared()).singleElement().satisfies(principal -> {
            assertThat(principal.id()).isEqualTo(carol.id());
            assertThat(principal.name()).isEqualTo("carol");
            assertThat(principal.role()).isEqualTo(PrincipalRole.LOWER);
            assertThat(principal.privateMarker()).contains("carol-marker");
            assertThat(principal.ownedObjects().all()).containsEntry("doc_id", "7");
            assertThat(principal.credentialHeaders().get()).containsEntry("Authorization", "Bearer carol-token");
        });
    }

    @Test
    void identityRolesMapOntoPrincipalRoles() {
        assertThat(IdentityRosterSource.principalRole(IdentityRole.PEER)).isEqualTo(PrincipalRole.PEER);
        assertThat(IdentityRosterSource.principalRole(IdentityRole.LOWER)).isEqualTo(PrincipalRole.LOWER);
        assertThat(IdentityRosterSource.principalRole(IdentityRole.HIGHER)).isEqualTo(PrincipalRole.HIGHER);
    }

    @Test
    void aCredentialRemovedAfterTheRosterIsBuiltStillProbesAsThatIdentity() {
        Identity bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));
        Principal principal = source.get().compared().get(0);

        registry.removeCredential(bob.id());

        assertThat(principal.credentialHeaders().get()).containsEntry("Authorization", "Bearer bob-token");
    }

    @Test
    void primaryCarriesTheConnectAsUsersPrivateMarkerAndOwnedObjects() {
        Identity alice = registry.add(Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER)
                .withPrivateMarker("ALICE-PRIVATE")
                .withOwnedValues(OwnedValues.entered(Map.of("doc_id", "d-alice-0001"))));
        registry.makePrimary(alice.id());

        Principal primary = source.get().primary();

        assertThat(primary.name()).isEqualTo("alice");
        assertThat(primary.privateMarker()).contains("ALICE-PRIVATE");
        assertThat(primary.ownedObjects().all()).containsEntry("doc_id", "d-alice-0001");
    }

    @Test
    void principalsOwnTheirPrivateMinedValuesButNotSharedOnes() {
        MinedSource source = new MinedSource("tools/call list_documents", "items[0].doc_id");
        Identity alice = registry.add(Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER)
                .withOwnedValues(OwnedValues.of(List.of(
                        new MinedValue("org_id", "acme", source, ValueScope.SHARED),
                        new MinedValue("doc_id", "d-alice-0001", source, ValueScope.PRIVATE)))));
        registry.makePrimary(alice.id());

        assertThat(this.source.get().primary().ownedObjects().all()).containsExactly(Map.entry("doc_id", "d-alice-0001"));
    }

    @Test
    void switchingConnectAsComparesThePreviousUserAndDropsTheNewOne() {
        Identity alice = registry.add(Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER));
        Identity bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-token"));
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));
        registry.makePrimary(alice.id());

        assertThat(source.get().compared()).extracting(Principal::name).containsExactly("bob");

        registry.makePrimary(bob.id());

        PrincipalRoster roster = source.get();
        assertThat(roster.primary().name()).isEqualTo("bob");
        assertThat(roster.compared()).extracting(Principal::name).containsExactly("alice");
    }
}
