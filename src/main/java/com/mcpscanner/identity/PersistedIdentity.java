package com.mcpscanner.identity;

import java.util.List;
import java.util.Optional;

record PersistedIdentity(
        String id,
        String name,
        IdentityRole role,
        String authKind,
        boolean compared,
        String privateMarker,
        List<PersistedOwnedValue> ownedObjects,
        IdentityOAuthSettings oauth,
        List<String> headerNames,
        boolean connectAs
) {
    static PersistedIdentity from(Identity identity, boolean connectAs) {
        return new PersistedIdentity(identity.id(), identity.name(), identity.role(), identity.authKind().name(),
                identity.compared(), identity.privateMarker().orElse(null), PersistedOwnedValue.fromValues(identity.ownedValues()),
                identity.oauth(), identity.headerNames(), connectAs);
    }

    Identity toIdentity(IdentityAuthKind legacyPrimaryKind) {
        return new Identity(id, name, role, authKindOr(legacyPrimaryKind), compared, Optional.ofNullable(privateMarker),
                PersistedOwnedValue.toValues(ownedObjects), oauth, headerNames);
    }

    private IdentityAuthKind authKindOr(IdentityAuthKind legacyPrimaryKind) {
        try {
            return IdentityAuthKind.valueOf(authKind);
        } catch (IllegalArgumentException | NullPointerException legacy) {
            return legacyPrimaryKind;
        }
    }
}
