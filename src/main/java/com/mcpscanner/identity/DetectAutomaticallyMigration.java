package com.mcpscanner.identity;

import java.util.List;

final class DetectAutomaticallyMigration {

    private DetectAutomaticallyMigration() {
    }

    static StoredIdentities apply(StoredIdentities stored, boolean autoDetectWasOn) {
        String primaryId = stored.primaryId().orElseThrow();
        if (!autoDetectWasOn || !primaryIsAuthNone(stored, primaryId)) {
            return stored;
        }
        List<Identity> migrated = stored.identities().stream()
                .map(identity -> identity.id().equals(primaryId) ? identity.withAuthKind(IdentityAuthKind.DETECT)
                        : identity)
                .toList();
        return new StoredIdentities(migrated, stored.primaryId());
    }

    private static boolean primaryIsAuthNone(StoredIdentities stored, String primaryId) {
        return stored.identities().stream()
                .anyMatch(identity -> identity.id().equals(primaryId) && identity.authKind() == IdentityAuthKind.NONE);
    }
}
