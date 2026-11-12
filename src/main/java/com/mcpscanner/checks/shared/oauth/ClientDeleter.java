package com.mcpscanner.checks.shared.oauth;

import java.util.Map;

final class ClientDeleter {

    private final OAuthHttp http;

    ClientDeleter(OAuthHttp http) {
        this.http = http;
    }

    boolean delete(ClientManagement management) {
        return http.delete(management.registrationClientUri(),
                        Map.of("Authorization", "Bearer " + management.registrationAccessToken()))
                .map(exchange -> exchange.response().statusCode())
                .filter(status -> status >= 200 && status < 300)
                .isPresent();
    }
}
