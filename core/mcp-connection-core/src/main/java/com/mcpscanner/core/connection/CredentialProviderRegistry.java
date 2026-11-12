package com.mcpscanner.core.connection;

public final class CredentialProviderRegistry {
    private final java.util.Map<String, CredentialProvider> providers;

    public CredentialProviderRegistry() { this(java.util.Map.of("none", CredentialProviders.none())); }
    private CredentialProviderRegistry(java.util.Map<String, CredentialProvider> providers) {
        this.providers = java.util.Map.copyOf(providers);
    }

    public CredentialProviderRegistry with(String id, CredentialProvider provider) {
        if (id == null || id.isBlank() || provider == null || providers.containsKey(id))
            throw new IllegalArgumentException("Unique provider ID required");
        var registered = new java.util.LinkedHashMap<>(providers);
        registered.put(id, provider);
        return new CredentialProviderRegistry(registered);
    }

    public CredentialProvider resolve(String id) {
        var provider = providers.get(id);
        if (provider == null) throw new IllegalArgumentException("Unknown credential provider");
        return provider;
    }
}
