package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.shared.oauth.AuthorizationParameters;
import com.mcpscanner.checks.shared.oauth.AuthorizationServer;

import java.net.URI;
import java.util.Map;

final class AuthorizationRequestFactory {

    private final AuthorizationServer server;

    AuthorizationRequestFactory(AuthorizationServer server) {
        this.server = server;
    }

    URI endpoint() {
        return server.authorizationEndpoint();
    }

    URI build(String clientId, URI callback, String state, AuthorizationVariant variant) {
        return AuthorizationParameters.withQuery(server.authorizationEndpoint(),
                parameters(clientId, callback, state, variant));
    }

    String form(String clientId, URI callback, String state, AuthorizationVariant variant) {
        return AuthorizationParameters.encode(parameters(clientId, callback, state, variant));
    }

    private Map<String, String> parameters(String clientId, URI callback, String state,
                                           AuthorizationVariant variant) {
        Map<String, String> parameters = AuthorizationParameters.of(server, variant.responseType(), clientId,
                callback, state);
        parameters.putAll(variant.extraParameters());
        return parameters;
    }
}
