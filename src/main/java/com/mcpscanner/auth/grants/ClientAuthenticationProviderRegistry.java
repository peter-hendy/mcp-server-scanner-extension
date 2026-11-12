package com.mcpscanner.auth.grants;

import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;

import java.util.List;

public final class ClientAuthenticationProviderRegistry {

    private static final String PRIVATE_KEY_JWT = "private_key_jwt";
    private static final ProviderRegistry.Rules<ClientAuthenticationProvider> RULES = new ProviderRegistry.Rules<>(
            ClientAuthenticationProvider::id,
            provider -> true,
            () -> new IllegalArgumentException("Configure unique client signer provider IDs"),
            () -> new IllegalArgumentException("Client signer providers could not be loaded; check installed registrations"),
            () -> new IllegalArgumentException("Install and select a registered private_key_jwt signer provider"));

    private final ProviderRegistry<ClientAuthenticationProvider> registry;

    public ClientAuthenticationProviderRegistry(List<ClientAuthenticationProvider> registrations) {
        this(new ProviderRegistry<>(registrations, RULES));
    }

    private ClientAuthenticationProviderRegistry(ProviderRegistry<ClientAuthenticationProvider> registry) {
        this.registry = registry;
    }

    public static ClientAuthenticationProviderRegistry load() {
        return new ClientAuthenticationProviderRegistry(ProviderRegistry.load(ClientAuthenticationProvider.class,
                ClientAuthenticationProviderRegistry.class.getClassLoader(), RULES));
    }

    public List<String> ids() {
        return registry.ids();
    }

    public ClientCredentialsAuthentication require(String id) {
        ClientCredentialsAuthentication authentication = configuredAuthentication(registry.require(id));
        if (authentication == null || !PRIVATE_KEY_JWT.equals(authentication.method())) {
            throw new IllegalArgumentException("The selected signer provider must configure private_key_jwt");
        }
        return authentication;
    }

    private static ClientCredentialsAuthentication configuredAuthentication(ClientAuthenticationProvider provider) {
        try {
            return provider.authentication();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("The selected client signer provider could not be configured");
        }
    }
}
