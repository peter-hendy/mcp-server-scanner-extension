package com.mcpscanner.auth.grants;

import com.mcpscanner.core.oauth.CredentialBinding;
import com.mcpscanner.core.oauth.OAuthGrantMode;
import com.mcpscanner.core.oauth.RegisteredClient;

final class GrantSettingsBinding {

    private GrantSettingsBinding() {
    }

    static CredentialBinding binding(OptionalGrantSettings settings, GuardedGrantMetadataDiscovery.Metadata metadata,
                                     OAuthGrantMode mode) {
        return new CredentialBinding(metadata.issuer(), metadata.resource().resource(), settings.clientId(),
                settings.scopes(), mode);
    }

    static RegisteredClient client(OptionalGrantSettings settings, GuardedGrantMetadataDiscovery.Metadata metadata) {
        return new RegisteredClient(metadata.issuer(), settings.clientId(), settings.clientSecret());
    }
}
