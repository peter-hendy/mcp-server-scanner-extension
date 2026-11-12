package com.mcpscanner.auth.grants;

import com.mcpscanner.auth.GrantBackedAuthStrategy;
import com.mcpscanner.core.oauth.EnterpriseAuthorizationMetadata;
import com.mcpscanner.core.oauth.EnterpriseManagedConfig;
import com.mcpscanner.core.oauth.OAuthGrantMode;
import com.mcpscanner.core.oauth.TokenExchange;

import java.time.Clock;

final class EnterpriseManagedGrantProfile implements GrantProfile {

    private final EnterpriseIdentityProviderRegistry providers;
    private final TokenExchange exchange;
    private final Clock clock;

    EnterpriseManagedGrantProfile(EnterpriseIdentityProviderRegistry providers, TokenExchange exchange, Clock clock) {
        this.providers = providers;
        this.exchange = exchange;
        this.clock = clock;
    }

    @Override
    public BoundGrant bind(OptionalGrantSettings settings) {
        var identityProvider = providers.require(settings.enterpriseProviderId());
        return (endpoint, metadata) -> GrantBackedAuthStrategy.enterpriseManaged(endpoint,
                config(settings, metadata), identityProvider, exchange, clock);
    }

    private static EnterpriseManagedConfig config(OptionalGrantSettings settings,
                                                  GuardedGrantMetadataDiscovery.Metadata metadata) {
        var server = new EnterpriseAuthorizationMetadata(metadata.issuer(), metadata.tokenEndpoint(),
                metadata.strings("authorization_grant_profiles_supported"), metadata.strings("token_endpoint_auth_methods_supported"),
                metadata.strings("token_endpoint_auth_signing_alg_values_supported"));
        return new EnterpriseManagedConfig(GrantSettingsBinding.binding(settings, metadata, OAuthGrantMode.ENTERPRISE_MANAGED),
                metadata.resource(), server, GrantSettingsBinding.client(settings, metadata), settings.authentication(), true);
    }
}
