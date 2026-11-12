package com.mcpscanner.checks.shared.oauth;

import java.net.URI;
import java.util.Optional;

public record AuthorizationServer(
        URI issuer,
        URI authorizationEndpoint,
        Optional<URI> registrationEndpoint,
        Optional<URI> tokenEndpoint,
        Optional<URI> revocationEndpoint,
        URI resource,
        Optional<String> scope) {
}
