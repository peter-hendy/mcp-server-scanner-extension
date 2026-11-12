package com.mcpscanner.identity;

import com.mcpscanner.config.AuthTypeNames;

final class LegacyAuthenticationKind {

    private LegacyAuthenticationKind() {
    }

    static IdentityAuthKind of(String authType) {
        if (authType == null) {
            return IdentityAuthKind.NONE;
        }
        return switch (authType) {
            case AuthTypeNames.BEARER -> IdentityAuthKind.BEARER;
            case AuthTypeNames.CUSTOM_HEADERS -> IdentityAuthKind.CUSTOM_HEADERS;
            case AuthTypeNames.OAUTH -> IdentityAuthKind.OAUTH;
            default -> IdentityAuthKind.NONE;
        };
    }
}
