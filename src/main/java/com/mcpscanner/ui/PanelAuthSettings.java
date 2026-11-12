package com.mcpscanner.ui;

import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.credentials.SharedAuthSettings;
import com.mcpscanner.ui.connect.OAuthGrantKinds;

import java.util.Optional;

final class PanelAuthSettings implements SharedAuthSettings {

    private final ServerConfigPanel panel;

    PanelAuthSettings(ServerConfigPanel panel) {
        this.panel = panel;
    }

    @Override
    public Optional<OAuthClientHints> oauthClient() {
        return panel.oauthConfig().validatedHints().value();
    }

    @Override
    public Optional<String> grantBlocker(IdentityAuthKind kind) {
        return OAuthGrantKinds.profileOf(kind).flatMap(panel.grantForms()::blocker);
    }
}
