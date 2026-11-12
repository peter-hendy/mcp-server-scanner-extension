package com.mcpscanner.identity;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.CredentialHeaders;

import java.util.List;
import java.util.Map;

public final class SharedCredentials {

    private final IdentityRegistry registry;

    public SharedCredentials(IdentityRegistry registry) {
        this.registry = registry;
    }

    public List<Identity> sharingWith(String id) {
        Map<String, String> own = headersOf(id);
        return registry.identities().stream()
                .filter(other -> !other.id().equals(id))
                .filter(other -> CredentialHeaders.same(own, headersOf(other.id())))
                .toList();
    }

    private Map<String, String> headersOf(String id) {
        return registry.credential(id).map(AuthStrategy::currentHeaders).orElse(Map.of());
    }
}
