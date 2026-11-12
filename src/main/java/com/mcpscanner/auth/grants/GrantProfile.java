package com.mcpscanner.auth.grants;

import com.mcpscanner.auth.GrantBackedAuthStrategy;

import java.net.URI;

@FunctionalInterface
interface GrantProfile {

    @FunctionalInterface
    interface BoundGrant {
        GrantBackedAuthStrategy strategyFor(URI endpoint, GuardedGrantMetadataDiscovery.Metadata metadata);
    }

    BoundGrant bind(OptionalGrantSettings settings);
}
