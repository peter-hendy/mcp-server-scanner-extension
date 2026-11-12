package com.mcpscanner.ui;

import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.ui.connect.ServerAuthSettings;

import java.util.Optional;

final class ServerConfigAuthSource implements ServerAuthSettings {

    private final ServerConfigPanel panel;

    ServerConfigAuthSource(ServerConfigPanel panel) {
        this.panel = panel;
    }

    @Override
    public OAuthClientHints oauthHints() {
        return panel.oauthConfig().buildOauthHintsSnapshot();
    }

    @Override
    public OptionalGrantSettings optionalGrantSettings(OptionalGrantSettings.Profile profile) {
        return panel.grantForms().snapshot(profile);
    }

    @Override
    public Optional<String> optionalGrantBlocker(OptionalGrantSettings.Profile profile) {
        return panel.grantForms().blocker(profile);
    }
}
