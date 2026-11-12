package com.mcpscanner.checks.catalog.oauthauthorizationconsent;

import com.mcpscanner.checks.shared.oauth.ClientMetadata;
import com.mcpscanner.checks.shared.oauth.ProbeCallback;
import com.mcpscanner.checks.shared.oauth.ProbeClientRegistrar;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

record ProbeClient(String clientId, URI callback) {

    private static final String CLIENT_NAME = "Burp MCP authorization consent probe";

    static Optional<ProbeClient> register(ProbeClientRegistrar registrar) {
        URI callback = ProbeCallback.loopback("scancheck-consent-" + UUID.randomUUID());
        ClientMetadata metadata = ClientMetadata.codeGrant(CLIENT_NAME, List.of(callback.toString()));
        return registrar.registerAddingRefreshGrantIfRequired(metadata).client()
                .map(client -> new ProbeClient(client.clientId(), callback));
    }

    boolean isCallback(URI location) {
        return ProbeCallback.matches(callback, location);
    }
}
