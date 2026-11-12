package com.mcpscanner.identity;

import java.util.List;
import java.util.Optional;

public record StoredIdentities(List<Identity> identities, Optional<String> primaryId) {

    public StoredIdentities {
        identities = List.copyOf(identities);
        primaryId = primaryId == null ? Optional.empty() : primaryId;
    }

    public static StoredIdentities of(List<Identity> identities, String primaryId) {
        return new StoredIdentities(identities, Optional.ofNullable(primaryId));
    }

    boolean hasPrimaryUser() {
        return primaryId.filter(id -> identities.stream().anyMatch(identity -> identity.id().equals(id)))
                .isPresent();
    }
}
