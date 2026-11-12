package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;

final class UserKindText {

    private UserKindText() {
    }

    static String of(IdentityUiServices services, Identity identity) {
        return services.registry().detected(identity.id()).map(AuthLabels::detected)
                .orElseGet(() -> AuthLabels.identityKind(identity.authKind()));
    }

    static String brief(IdentityUiServices services, Identity identity) {
        return services.registry().detected(identity.id()).map(AuthLabels::detected)
                .orElseGet(() -> AuthLabels.identityKindShort(identity.authKind()));
    }
}
