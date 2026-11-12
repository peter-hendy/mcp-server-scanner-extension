package com.mcpscanner.identity;

import java.util.Optional;

public record LegacyPrimary(IdentityAuthKind authKind, Optional<String> privateMarker,
                            OwnedValues ownedValues) {

    public LegacyPrimary {
        privateMarker = privateMarker == null ? Optional.empty() : privateMarker.filter(marker -> !marker.isBlank());
        ownedValues = ownedValues == null ? OwnedValues.NONE : ownedValues;
    }

    Identity asUser(String name) {
        return Identity.create(name, IdentityRole.PEER, authKind)
                .withPrivateMarker(privateMarker.orElse(null))
                .withOwnedValues(ownedValues);
    }
}
