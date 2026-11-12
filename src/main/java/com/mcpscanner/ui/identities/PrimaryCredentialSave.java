package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;

final class PrimaryCredentialSave {

    private final IdentityUiServices services;

    PrimaryCredentialSave(IdentityUiServices services) {
        this.services = services;
    }

    void saveToken(String token) {
        if (token.isBlank()) {
            throw new IllegalArgumentException("Enter the bearer token");
        }
        services.credentials().supplyToken(bearerPrimary().id(), token);
    }

    private Identity bearerPrimary() {
        Identity primary = services.registry().primaryUser();
        if (primary.authKind() == IdentityAuthKind.BEARER) {
            return primary;
        }
        services.credentials().clear(primary.id());
        Identity switched = primary.withAuthKind(IdentityAuthKind.BEARER);
        services.registry().update(switched);
        return switched;
    }
}
