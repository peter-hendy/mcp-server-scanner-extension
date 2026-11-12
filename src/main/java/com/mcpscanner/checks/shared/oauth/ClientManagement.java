package com.mcpscanner.checks.shared.oauth;

import java.net.URI;
import java.util.Optional;

public record ClientManagement(URI registrationClientUri, String registrationAccessToken) {

    static Optional<ClientManagement> from(JsonDocument registration, URI registrationEndpoint) {
        return registration.webUri("registration_client_uri")
                .filter(uri -> HttpUris.sameOrigin(uri, registrationEndpoint))
                .flatMap(uri -> registration.text("registration_access_token")
                        .map(token -> new ClientManagement(uri, token)));
    }
}
