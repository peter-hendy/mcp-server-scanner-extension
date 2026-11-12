package com.mcpscanner.identity;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.HttpOrigin;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class MinedValueOriginGuard {

    private final IdentityRegistry registry;
    private final OwnedValueStore store;
    private final McpEventLog eventLog;
    private final Supplier<String> currentEndpoint;
    private Optional<HttpOrigin> minedAt = Optional.empty();

    private MinedValueOriginGuard(IdentityRegistry registry, McpEventLog eventLog, Supplier<String> currentEndpoint) {
        this.registry = registry;
        this.store = new RegistryOwnedValues(registry);
        this.eventLog = eventLog;
        this.currentEndpoint = currentEndpoint;
    }

    public static MinedValueOriginGuard watching(IdentityRegistry registry, McpEventLog eventLog,
                                                 Supplier<String> currentEndpoint) {
        MinedValueOriginGuard guard = new MinedValueOriginGuard(registry, eventLog, currentEndpoint);
        guard.bind();
        registry.addListener(guard::bind);
        return guard;
    }

    public synchronized void commit(String endpoint) {
        Optional<HttpOrigin> committed = originOf(endpoint);
        if (committed.isEmpty()) {
            return;
        }
        bind();
        if (minedAt.isEmpty() && !holders().isEmpty()) {
            minedAt = committed;
        }
        minedAt.filter(origin -> !origin.equals(committed.get()))
                .ifPresent(previous -> clear(previous, committed.get()));
    }

    private synchronized void bind() {
        if (holders().isEmpty()) {
            minedAt = Optional.empty();
        } else if (minedAt.isEmpty()) {
            minedAt = originOf(currentEndpoint.get());
        }
    }

    private void clear(HttpOrigin previous, HttpOrigin committed) {
        List<String> names = holders().stream().map(Identity::name).toList();
        store.clearAllMined();
        minedAt = Optional.empty();
        eventLog.info("Endpoint origin changed from " + previous + " to " + committed
                + ": cleared the collected IDs of " + String.join(", ", names));
    }

    private List<Identity> holders() {
        return registry.identities().stream().filter(identity -> !identity.ownedValues().mined().isEmpty()).toList();
    }

    private static Optional<HttpOrigin> originOf(String endpoint) {
        return endpoint == null ? Optional.empty() : HttpOrigin.parse(endpoint.trim());
    }
}
