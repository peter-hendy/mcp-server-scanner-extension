package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.shared.oauth.RegisteredClient;

import java.net.URI;

record OwnedClient(RegisteredClient registration, URI callback) {

    String clientId() {
        return registration.clientId();
    }
}
