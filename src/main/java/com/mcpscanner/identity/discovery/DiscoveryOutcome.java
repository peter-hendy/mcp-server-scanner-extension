package com.mcpscanner.identity.discovery;

import java.util.Objects;

public sealed interface DiscoveryOutcome {

    enum Discovering implements DiscoveryOutcome { INSTANCE }

    record Listed(IdentityListing listing) implements DiscoveryOutcome {
        public Listed {
            Objects.requireNonNull(listing, "listing");
        }
    }

    record Failed(String reason) implements DiscoveryOutcome {
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
