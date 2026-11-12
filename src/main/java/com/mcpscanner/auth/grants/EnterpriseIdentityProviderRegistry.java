package com.mcpscanner.auth.grants;

import com.mcpscanner.core.oauth.EnterpriseIdentityProvider;
import com.mcpscanner.core.oauth.OAuthFlowException;

import java.util.List;
import java.util.Map;

public final class EnterpriseIdentityProviderRegistry {

    private static final ProviderRegistry.Rules<EnterpriseIdentityProviderRegistration> RULES = new ProviderRegistry.Rules<>(
            EnterpriseIdentityProviderRegistration::id,
            registration -> registration.provider() != null,
            () -> invalidConfiguration("Enterprise identity provider registration is invalid or duplicated"),
            () -> invalidConfiguration("Enterprise identity provider registration could not be loaded"),
            () -> new OAuthFlowException(OAuthFlowException.Reason.MISSING_CREDENTIAL,
                    "Select a registered enterprise identity provider"));

    private record MappedRegistration(String id, EnterpriseIdentityProvider provider)
            implements EnterpriseIdentityProviderRegistration {
        @Override
        public String toString() {
            return "EnterpriseIdentityProviderRegistration[redacted]";
        }
    }

    private final ProviderRegistry<EnterpriseIdentityProviderRegistration> registry;

    public EnterpriseIdentityProviderRegistry(Map<String, EnterpriseIdentityProvider> providers) {
        this(providers.entrySet().stream()
                .<EnterpriseIdentityProviderRegistration>map(entry -> new MappedRegistration(entry.getKey(), entry.getValue()))
                .toList());
    }

    public EnterpriseIdentityProviderRegistry(List<? extends EnterpriseIdentityProviderRegistration> registrations) {
        this(new ProviderRegistry<>(registrations, RULES));
    }

    private EnterpriseIdentityProviderRegistry(ProviderRegistry<EnterpriseIdentityProviderRegistration> registry) {
        this.registry = registry;
    }

    public static EnterpriseIdentityProviderRegistry load() {
        return load(EnterpriseIdentityProviderRegistry.class.getClassLoader());
    }

    public static EnterpriseIdentityProviderRegistry load(ClassLoader loader) {
        return new EnterpriseIdentityProviderRegistry(
                ProviderRegistry.load(EnterpriseIdentityProviderRegistration.class, loader, RULES));
    }

    public static EnterpriseIdentityProviderRegistry loadOrEmpty() {
        try {
            return load();
        } catch (OAuthFlowException unavailable) {
            return new EnterpriseIdentityProviderRegistry(List.<EnterpriseIdentityProviderRegistration>of());
        }
    }

    public List<String> ids() {
        return registry.ids();
    }

    public EnterpriseIdentityProvider require(String id) {
        return registry.require(id).provider();
    }

    private static OAuthFlowException invalidConfiguration(String message) {
        return new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION, message);
    }
}
