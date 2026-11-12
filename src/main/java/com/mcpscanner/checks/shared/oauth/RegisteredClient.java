package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;
import java.util.Optional;

public record RegisteredClient(String clientId, List<String> redirectUris, HttpRequestResponse evidence,
                               Optional<ClientManagement> management) {

    public RegisteredClient {
        redirectUris = List.copyOf(redirectUris);
    }

    public boolean lists(String redirectUri) {
        return redirectUris.stream().anyMatch(redirectUri::equalsIgnoreCase);
    }
}
