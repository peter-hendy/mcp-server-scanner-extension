package com.mcpscanner.auth.grants;

import com.mcpscanner.auth.GrantBackedAuthStrategy;
import com.mcpscanner.core.oauth.ClientCredentialsConfig;
import com.mcpscanner.core.oauth.ClientCredentialsMetadata;
import com.mcpscanner.core.oauth.OAuthGrantMode;
import com.mcpscanner.core.oauth.TokenExchange;

import java.time.Clock;

final class ClientCredentialsGrantProfile implements GrantProfile {

    private final TokenExchange exchange;
    private final Clock clock;

    ClientCredentialsGrantProfile(TokenExchange exchange, Clock clock) {
        this.exchange = exchange;
        this.clock = clock;
    }

    @Override
    public BoundGrant bind(OptionalGrantSettings settings) {
        return (endpoint, metadata) -> GrantBackedAuthStrategy.clientCredentials(endpoint,
                config(settings, metadata), exchange, clock);
    }

    private static ClientCredentialsConfig config(OptionalGrantSettings settings,
                                                  GuardedGrantMetadataDiscovery.Metadata metadata) {
        var server = new ClientCredentialsMetadata(metadata.issuer(), metadata.tokenEndpoint(),
                metadata.strings("grant_types_supported"), metadata.strings("token_endpoint_auth_methods_supported"),
                metadata.strings("token_endpoint_auth_signing_alg_values_supported"));
        return new ClientCredentialsConfig(GrantSettingsBinding.binding(settings, metadata, OAuthGrantMode.CLIENT_CREDENTIALS),
                metadata.resource(), server, GrantSettingsBinding.client(settings, metadata), settings.authentication());
    }
}
