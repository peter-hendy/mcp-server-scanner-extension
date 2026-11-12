package com.mcpscanner.identity;

import java.util.List;
import java.util.Optional;

record LegacyPrimaryProfile(String privateMarker, List<PersistedOwnedValue> ownedObjects) {

    static final LegacyPrimaryProfile EMPTY = new LegacyPrimaryProfile(null, List.of());

    LegacyPrimary withAuthKind(IdentityAuthKind kind) {
        return new LegacyPrimary(kind, Optional.ofNullable(privateMarker), PersistedOwnedValue.toValues(ownedObjects));
    }
}
