package com.mcpscanner.ui.connect;

import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.identity.IdentityAuthKind;

import java.util.Optional;

public final class OAuthGrantKinds {

    private OAuthGrantKinds() {
    }

    public static Optional<OptionalGrantSettings.Profile> profileOf(IdentityAuthKind kind) {
        return switch (kind) {
            case OAUTH_CLIENT_CREDENTIALS -> Optional.of(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS);
            case OAUTH_ENTERPRISE_MANAGED -> Optional.of(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED);
            default -> Optional.empty();
        };
    }
}
