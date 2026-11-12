package com.mcpscanner.identity.discovery;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.PrimaryConnection;
import com.mcpscanner.identity.FailureReason;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.logging.McpEventLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class IdentityDiscoveryService {

    private final IdentityRegistry registry;
    private final Function<McpServerConfig, ConnectResult> discoverer;
    private final IdentityListings listings;
    private final Executor executor;
    private final McpEventLog eventLog;
    private final Map<String, AuthStrategy> discoveredWith = new HashMap<>();
    private Optional<PrimaryConnection> target = Optional.empty();
    private long generation;

    public IdentityDiscoveryService(IdentityRegistry registry, Function<McpServerConfig, ConnectResult> discoverer,
                                    IdentityListings listings, Executor executor, McpEventLog eventLog) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.discoverer = Objects.requireNonNull(discoverer, "discoverer");
        this.listings = Objects.requireNonNull(listings, "listings");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
    }

    public void start() {
        registry.addListener(this::onRegistryChanged);
    }

    public IdentityListings listings() {
        return listings;
    }

    public synchronized void onPrimaryConnected(PrimaryConnection connection) {
        resetFor(Optional.of(connection));
        listings.record(Principal.PRIMARY_ID, new DiscoveryOutcome.Listed(IdentityListing.of(connection.catalog())));
        registry.compared().forEach(this::discover);
    }

    public synchronized void onDisconnected() {
        resetFor(Optional.empty());
    }

    public synchronized void refreshAll() {
        if (target.isPresent()) {
            discoveredWith.clear();
            registry.compared().forEach(this::discover);
        }
    }

    private synchronized void onRegistryChanged() {
        if (target.isEmpty()) {
            return;
        }
        List<Identity> compared = registry.compared();
        forgetIdentitiesNoLongerCompared(compared);
        compared.stream().filter(this::credentialChangedSinceDiscovery).forEach(this::discover);
    }

    private void resetFor(Optional<PrimaryConnection> connection) {
        generation++;
        target = connection;
        discoveredWith.clear();
        listings.clear();
    }

    private void forgetIdentitiesNoLongerCompared(List<Identity> compared) {
        Set<String> kept = new HashSet<>();
        kept.add(Principal.PRIMARY_ID);
        compared.forEach(identity -> kept.add(identity.id()));
        discoveredWith.keySet().retainAll(kept);
        listings.retainOnly(kept);
    }

    private boolean credentialChangedSinceDiscovery(Identity identity) {
        return registry.credential(identity.id()).orElse(null) != discoveredWith.get(identity.id());
    }

    private void discover(Identity identity) {
        registry.credential(identity.id()).ifPresent(credential -> discover(identity, credential));
    }

    private void discover(Identity identity, AuthStrategy credential) {
        PrimaryConnection connection = target.orElseThrow();
        long attempt = generation;
        discoveredWith.put(identity.id(), credential);
        listings.record(identity.id(), DiscoveryOutcome.Discovering.INSTANCE);
        executor.execute(() -> publish(identity, credential, attempt, discoverAs(identity, credential, connection)));
    }

    private DiscoveryOutcome discoverAs(Identity identity, AuthStrategy credential, PrimaryConnection connection) {
        try {
            ConnectResult catalog = discoverer.apply(
                    new McpServerConfig(connection.endpoint(), connection.transport(), credential));
            IdentityListing listing = IdentityListing.of(catalog);
            eventLog.info("Identity \"" + identity.name() + "\" lists " + listing.toolNames().size() + " tools and "
                    + listing.resourceUris().size() + " resources");
            return new DiscoveryOutcome.Listed(listing);
        } catch (RuntimeException failure) {
            String reason = FailureReason.of(failure);
            eventLog.warn("Identity \"" + identity.name() + "\" discovery failed: " + reason);
            return new DiscoveryOutcome.Failed(reason);
        }
    }

    private synchronized void publish(Identity identity, AuthStrategy credential, long attempt,
                                      DiscoveryOutcome outcome) {
        if (attempt == generation && discoveredWith.get(identity.id()) == credential) {
            listings.record(identity.id(), outcome);
        }
    }
}
