package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.Optional;

public record Registration(RegistrationStatus status, Optional<RegisteredClient> client,
                           Optional<HttpRequestResponse> exchange) {

    public static Registration inconclusive(Optional<HttpRequestResponse> exchange) {
        return new Registration(RegistrationStatus.INCONCLUSIVE, Optional.empty(), exchange);
    }

    public static Registration rejected(HttpRequestResponse exchange) {
        return new Registration(RegistrationStatus.REJECTED, Optional.empty(), Optional.of(exchange));
    }

    public static Registration accepted(RegisteredClient client) {
        return new Registration(RegistrationStatus.ACCEPTED, Optional.of(client), Optional.of(client.evidence()));
    }

    public Optional<RegisteredClient> clientListingAll(Iterable<String> redirectUris) {
        return client.filter(registered -> {
            for (String redirectUri : redirectUris) {
                if (!registered.lists(redirectUri)) {
                    return false;
                }
            }
            return true;
        });
    }
}
