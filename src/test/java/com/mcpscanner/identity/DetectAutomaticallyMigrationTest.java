package com.mcpscanner.identity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DetectAutomaticallyMigrationTest {

    private final Identity userOne = Identity.create("User 1", IdentityRole.PEER, IdentityAuthKind.NONE);
    private final Identity bob = Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.NONE);

    @Test
    void anAuthNonePrimaryUserDetectsAutomaticallyWhileTheCheckboxWasOn() {
        StoredIdentities migrated = DetectAutomaticallyMigration.apply(stored(userOne), true);

        assertThat(migrated.identities()).extracting(Identity::authKind)
                .containsExactly(IdentityAuthKind.DETECT, IdentityAuthKind.NONE);
    }

    @Test
    void aProjectThatTurnedTheCheckboxOffKeepsAuthNone() {
        StoredIdentities stored = stored(userOne);

        assertThat(DetectAutomaticallyMigration.apply(stored, false)).isSameAs(stored);
    }

    @Test
    void aPrimaryUserWithAnotherKindIsUnchanged() {
        StoredIdentities stored = stored(userOne.withAuthKind(IdentityAuthKind.BEARER));

        assertThat(DetectAutomaticallyMigration.apply(stored, true)).isSameAs(stored);
    }

    private StoredIdentities stored(Identity primary) {
        return new StoredIdentities(List.of(primary, bob), Optional.of(primary.id()));
    }
}
