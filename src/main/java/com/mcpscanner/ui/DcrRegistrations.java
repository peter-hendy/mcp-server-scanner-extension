package com.mcpscanner.ui;

import com.mcpscanner.auth.oauth.OAuthSession;
import com.mcpscanner.config.ExtensionConfigStore;

import java.net.URI;
import java.util.Optional;

final class DcrRegistrations {

    record Registration(String managementToken, String clientUri) {
    }

    private final ExtensionConfigStore store;

    DcrRegistrations(ExtensionConfigStore store) {
        this.store = store;
    }

    boolean remember(URI issuer, OAuthSession session) {
        if (issuer == null) {
            return false;
        }
        String key = issuer.toString();
        session.registrationAccessToken().ifPresent(token -> store.setDcrRegistrationManagementToken(key, token));
        session.registrationClientUri().ifPresent(uri -> store.setDcrRegistrationClientUri(key, uri));
        return session.registrationAccessToken().isPresent();
    }

    Optional<Registration> forIssuer(String issuer) {
        String key = issuer.trim();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        String token = store.dcrRegistrationManagementToken(key);
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String clientUri = store.dcrRegistrationClientUri(key);
        return Optional.of(new Registration(token, clientUri == null ? "" : clientUri));
    }
}
