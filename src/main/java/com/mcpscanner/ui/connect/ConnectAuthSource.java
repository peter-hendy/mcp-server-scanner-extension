package com.mcpscanner.ui.connect;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.auth.oauth.OAuthClientHints;

import java.util.Optional;

public interface ConnectAuthSource {

    boolean isOptionalGrantSelected();

    OAuthClientHints oauthHints();

    OptionalGrantSettings optionalGrantSettings();

    AuthStrategy currentAuthStrategy();

    String connectingUserId();

    default Optional<AuthStrategy> signedInSession() {
        return Optional.empty();
    }
}
