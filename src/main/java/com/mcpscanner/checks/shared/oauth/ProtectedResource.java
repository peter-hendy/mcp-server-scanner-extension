package com.mcpscanner.checks.shared.oauth;

import java.net.URI;
import java.util.Optional;

public record ProtectedResource(Optional<URI> resource, URI authorizationServer, Optional<String> scope) {
}
