package com.mcpscanner.ui.connect;

import com.mcpscanner.config.AuthTypeNames;
import com.mcpscanner.ui.OAuthGrant;
import com.mcpscanner.ui.state.ConnectionStatus;
import com.mcpscanner.core.oauth.RegistrationMode;

import java.util.Optional;

final class AutoAuthLabel {

    private static final String PREFIX = "Detect → ";

    private AutoAuthLabel() {
    }

    static String noAuth() {
        return PREFIX + AuthTypeNames.NONE;
    }

    static String oauth(Optional<RegistrationMode> registration) {
        String details = OAuthGrant.AUTHORIZATION_CODE.label()
                + registration.map(mode -> ", " + ConnectionStatus.registrationLabel(mode)).orElse("");
        return PREFIX + AuthTypeNames.OAUTH + " (" + details + ")";
    }
}
