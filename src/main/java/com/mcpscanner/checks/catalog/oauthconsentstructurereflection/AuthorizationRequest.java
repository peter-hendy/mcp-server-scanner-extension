package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.oauth.AuthorizationParameters;
import com.mcpscanner.checks.shared.oauth.AuthorizationServer;

import java.net.URI;

record AuthorizationRequest(AuthorizationServer server, String clientId, URI redirectUri, String state) {

    AuthorizationRequest forClient(String otherClientId) {
        return new AuthorizationRequest(server, otherClientId, redirectUri, state);
    }

    URI uri() {
        return AuthorizationParameters.withQuery(server.authorizationEndpoint(),
                AuthorizationParameters.of(server, "code", clientId, redirectUri, state));
    }
}
