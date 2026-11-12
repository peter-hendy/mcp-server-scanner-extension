package com.mcpscanner.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class PrimaryUserMigration {

    private static final String USER = "User";

    private PrimaryUserMigration() {
    }

    static StoredIdentities ensurePrimary(StoredIdentities stored, Supplier<LegacyPrimary> legacy) {
        if (stored.hasPrimaryUser()) {
            return stored;
        }
        Identity userOne = legacy.get().asUser(IdentityNames.of(stored.identities()).unusedNumbered(USER));
        List<Identity> migrated = new ArrayList<>();
        migrated.add(userOne);
        migrated.addAll(stored.identities());
        return new StoredIdentities(migrated, Optional.of(userOne.id()));
    }
}
