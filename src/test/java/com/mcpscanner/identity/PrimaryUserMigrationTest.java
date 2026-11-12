package com.mcpscanner.identity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PrimaryUserMigrationTest {

    private static final LegacyPrimary ALICE_PROFILE = new LegacyPrimary(IdentityAuthKind.BEARER,
            Optional.of("ALICE-PRIVATE"), OwnedValues.entered(Map.of("doc_id", "d-alice-0001")));

    @Test
    void aProjectWithNoConnectAsUserGetsUserOneBuiltFromTheSavedAuthentication() {
        Identity bob = Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);

        StoredIdentities migrated = PrimaryUserMigration.ensurePrimary(
                new StoredIdentities(List.of(bob), Optional.empty()), () -> ALICE_PROFILE);

        Identity userOne = migrated.identities().get(0);
        assertThat(migrated.identities()).hasSize(2).endsWith(bob);
        assertThat(migrated.primaryId()).contains(userOne.id());
        assertThat(userOne.name()).isEqualTo("User 1");
        assertThat(userOne.role()).isEqualTo(IdentityRole.PEER);
        assertThat(userOne.authKind()).isEqualTo(IdentityAuthKind.BEARER);
        assertThat(userOne.privateMarker()).contains("ALICE-PRIVATE");
        assertThat(userOne.ownedValues()).isEqualTo(OwnedValues.entered(Map.of("doc_id", "d-alice-0001")));
    }

    @Test
    void aProjectThatAlreadyHasAConnectAsUserIsUnchanged() {
        Identity alice = Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER);
        StoredIdentities stored = StoredIdentities.of(List.of(alice), alice.id());

        assertThat(PrimaryUserMigration.ensurePrimary(stored, () -> ALICE_PROFILE)).isSameAs(stored);
    }

    @Test
    void aConnectAsIdNamingNoStoredUserIsMigrated() {
        Identity alice = Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER);

        StoredIdentities migrated = PrimaryUserMigration.ensurePrimary(
                StoredIdentities.of(List.of(alice), "gone"), () -> ALICE_PROFILE);

        assertThat(migrated.identities()).hasSize(2);
        assertThat(migrated.primaryId()).contains(migrated.identities().get(0).id());
    }

    @Test
    void userOneTakesTheFirstFreeUserName() {
        Identity taken = Identity.create("user 1", IdentityRole.PEER, IdentityAuthKind.BEARER);

        StoredIdentities migrated = PrimaryUserMigration.ensurePrimary(
                new StoredIdentities(List.of(taken), Optional.empty()), () -> ALICE_PROFILE);

        assertThat(migrated.identities().get(0).name()).isEqualTo("User 2");
    }
}
