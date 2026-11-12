package com.mcpscanner.ui;

import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;

import java.net.URI;
import java.util.Optional;

final class DiscoveryMemory {

    private record Entry(URI endpoint, DiscoveredMetadata metadata) {
    }

    private volatile Entry last;

    DiscoveredMetadata remember(URI endpoint, DiscoveredMetadata metadata) {
        last = new Entry(endpoint, metadata);
        return metadata;
    }

    Optional<DiscoveredMetadata> recallFor(URI endpoint) {
        Entry entry = last;
        return entry != null && entry.endpoint().equals(endpoint) ? Optional.of(entry.metadata()) : Optional.empty();
    }
}
