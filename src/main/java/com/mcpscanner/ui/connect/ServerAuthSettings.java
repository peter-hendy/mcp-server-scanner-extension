package com.mcpscanner.ui.connect;

import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.auth.oauth.OAuthClientHints;

import java.util.Optional;

public interface ServerAuthSettings {

    OAuthClientHints oauthHints();

    OptionalGrantSettings optionalGrantSettings(OptionalGrantSettings.Profile profile);

    default Optional<String> optionalGrantBlocker(OptionalGrantSettings.Profile profile) {
        return Optional.empty();
    }
}
