package com.mcpscanner.identity.discovery;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.PrimaryConnection;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityDiscoveryServiceTest {

    private static final String ENDPOINT = "http://127.0.0.1:9/mcp";
    private static final AccessItemKey SHARED_TOOL = new AccessItemKey(AccessItemKey.Kind.TOOL, "search");
    private static final AccessItemKey ADMIN_TOOL = new AccessItemKey(AccessItemKey.Kind.TOOL, "delete_project");

    private final List<McpServerConfig> discoveredConfigs = new ArrayList<>();
    private final Map<String, Function<McpServerConfig, ConnectResult>> serverByToken = new ConcurrentHashMap<>();
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final IdentityListings listings = new IdentityListings();
    private IdentityRegistry registry;
    private Executor executor = Runnable::run;
    private IdentityDiscoveryService service;

    @BeforeEach
    void setUp() {
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        service = newService();
    }

    @Test
    void recordsThePrimaryListingFromItsOwnConnect() {
        service.onPrimaryConnected(primaryConnection("search", "delete_project"));

        assertThat(listings.lists(Principal.PRIMARY_ID, ADMIN_TOOL)).contains(true);
        assertThat(discoveredConfigs).isEmpty();
    }

    @Test
    void discoversEachComparedIdentityWithItsOwnCredentialOnTheResolvedTransport() {
        Identity bob = withToken(peer("bob"), "bob");
        Identity carol = withToken(lower("carol"), "carol");
        serves("bob", "search", "delete_project");
        serves("carol", "search");

        service.onPrimaryConnected(primaryConnection("search", "delete_project"));

        assertThat(discoveredConfigs).extracting(McpServerConfig::endpoint).containsOnly(ENDPOINT);
        assertThat(discoveredConfigs).extracting(McpServerConfig::transport).containsOnly(TransportType.SSE);
        assertThat(discoveredConfigs).extracting(config -> config.auth().headers().get("Authorization"))
                .containsExactly("Bearer bob", "Bearer carol");
        assertThat(listings.lists(bob.id(), ADMIN_TOOL)).contains(true);
        assertThat(listings.lists(carol.id(), ADMIN_TOOL)).contains(false);
        assertThat(listings.lists(carol.id(), SHARED_TOOL)).contains(true);
    }

    @Test
    void oneFailingIdentityLeavesThePrimaryAndOtherIdentitiesListed() {
        Identity bob = withToken(peer("bob"), "bob");
        Identity carol = withToken(lower("carol"), "carol");
        serverByToken.put("Bearer bob", config -> {
            throw new IllegalStateException("Failed to establish scanner session",
                    new java.io.IOException("401 Unauthorized"));
        });
        serves("carol", "search");

        service.onPrimaryConnected(primaryConnection("search"));

        assertThat(listings.outcome(bob.id())).get().isInstanceOfSatisfying(DiscoveryOutcome.Failed.class,
                failed -> assertThat(failed.reason()).contains("401 Unauthorized"));
        assertThat(listings.lists(bob.id(), SHARED_TOOL)).isEmpty();
        assertThat(listings.lists(carol.id(), SHARED_TOOL)).contains(true);
        assertThat(listings.lists(Principal.PRIMARY_ID, SHARED_TOOL)).contains(true);
    }

    @Test
    void skipsIdentitiesWithoutCredentialsOrNotCompared() {
        registry.add(peer("dave"));
        Identity erin = withToken(peer("erin"), "erin");
        registry.update(erin.withCompared(false));

        service.onPrimaryConnected(primaryConnection("search"));

        assertThat(discoveredConfigs).isEmpty();
        assertThat(listings.outcome(erin.id())).isEmpty();
    }

    @Test
    void discoversAnIdentityThatGainsCredentialsWhileConnected() {
        service.onPrimaryConnected(primaryConnection("search"));
        serves("bob", "search");

        Identity bob = withToken(peer("bob"), "bob");

        assertThat(listings.lists(bob.id(), SHARED_TOOL)).contains(true);
        assertThat(discoveredConfigs).hasSize(1);
    }

    @Test
    void doesNotDiscoverWhileDisconnected() {
        withToken(peer("bob"), "bob");

        assertThat(discoveredConfigs).isEmpty();
    }

    @Test
    void refreshRunsEveryComparedIdentityAgain() {
        withToken(peer("bob"), "bob");
        serves("bob", "search");
        service.onPrimaryConnected(primaryConnection("search"));

        service.refreshAll();

        assertThat(discoveredConfigs).hasSize(2);
    }

    @Test
    void disconnectClearsListingsAndDiscardsLateResults() {
        executor = pending::add;
        service = newService();
        Identity bob = withToken(peer("bob"), "bob");
        serves("bob", "search");
        service.onPrimaryConnected(primaryConnection("search"));
        assertThat(listings.outcome(bob.id())).contains(DiscoveryOutcome.Discovering.INSTANCE);

        service.onDisconnected();
        runPending();

        assertThat(listings.outcomes()).isEmpty();
    }

    @Test
    void removedIdentitiesLoseTheirListing() {
        Identity bob = withToken(peer("bob"), "bob");
        serves("bob", "search");
        service.onPrimaryConnected(primaryConnection("search"));

        registry.remove(bob.id());

        assertThat(listings.outcome(bob.id())).isEmpty();
    }

    @Test
    void notifiesListenersWhenListingsChange() {
        List<Map<String, DiscoveryOutcome>> published = new ArrayList<>();
        listings.addListener(() -> published.add(listings.outcomes()));

        service.onPrimaryConnected(primaryConnection("search"));

        assertThat(published).isNotEmpty();
        assertThat(published.get(published.size() - 1)).containsKey(Principal.PRIMARY_ID);
    }

    private IdentityDiscoveryService newService() {
        IdentityDiscoveryService created = new IdentityDiscoveryService(registry, this::discover, listings,
                command -> executor.execute(command), McpEventLog.noop());
        created.start();
        return created;
    }

    private ConnectResult discover(McpServerConfig config) {
        discoveredConfigs.add(config);
        String authorization = config.auth().headers().get("Authorization");
        return serverByToken.getOrDefault(authorization, ignored -> catalog()).apply(config);
    }

    private void serves(String token, String... tools) {
        serverByToken.put("Bearer " + token, config -> catalog(tools));
    }

    private void runPending() {
        while (!pending.isEmpty()) {
            pending.poll().run();
        }
    }

    private Identity withToken(Identity identity, String token) {
        Identity added = registry.add(identity);
        AuthStrategy credential = new BearerTokenAuthStrategy(token);
        registry.setCredential(added.id(), credential);
        return added;
    }

    private static Identity peer(String name) {
        return Identity.create(name, IdentityRole.PEER, IdentityAuthKind.BEARER);
    }

    private static Identity lower(String name) {
        return Identity.create(name, IdentityRole.LOWER, IdentityAuthKind.BEARER);
    }

    private static PrimaryConnection primaryConnection(String... tools) {
        return new PrimaryConnection(ENDPOINT, TransportType.SSE, catalog(tools));
    }

    static ConnectResult catalog(String... tools) {
        List<McpToolDefinition> definitions = java.util.Arrays.stream(tools)
                .map(IdentityDiscoveryServiceTest::tool).toList();
        return new ConnectResult(definitions, List.<McpResourceDefinition>of(),
                List.<McpResourceTemplateDefinition>of(), List.<McpPromptDefinition>of(), ServerMetadata.empty());
    }

    private static McpToolDefinition tool(String name) {
        return new McpToolDefinition(name, "", "{}");
    }
}
