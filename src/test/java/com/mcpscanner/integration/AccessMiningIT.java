package com.mcpscanner.integration;

import com.mcpscanner.access.IdentityRequests;
import com.mcpscanner.access.mining.AccessMining;
import com.mcpscanner.access.mining.MiningInputs;
import com.mcpscanner.access.mining.MiningListener;
import com.mcpscanner.access.mining.MiningPreferences;
import com.mcpscanner.access.mining.MiningReport;
import com.mcpscanner.access.mining.MiningStop;
import com.mcpscanner.access.mining.RoutedMiningSender;
import com.mcpscanner.access.mining.WriteCallApproval;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.McpDiscoveryClient;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.identity.IdentityRosterSource;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.identity.MinedValue;
import com.mcpscanner.identity.RegistryOwnedValues;
import com.mcpscanner.identity.ValueScope;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpRepeaterRequestFactory;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class AccessMiningIT {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void miningAsAliceAndBobStoresEachUsersPrivateValues() throws Exception {
        ExtensionConfigStore config = InMemoryPersistedObject.configStore();
        IdentityRegistry registry = new IdentityRegistry(new IdentityStore(config, mock(McpEventLog.class)));
        Identity alice = registry.primaryUser().withName("alice");
        registry.update(alice);
        Identity bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER)
                .withCompared(true));
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));
        RegistryOwnedValues store = new RegistryOwnedValues(registry);
        ExecutorService worker = Executors.newSingleThreadExecutor();

        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer("--default-mode", "safe");
             ScannerProxyFixture proxy = ScannerProxyFixture.connect(McpCheckItSupport.mcpEndpoint(server.port()),
                     TransportType.STREAMABLE_HTTP)) {
            IdentityRosterSource roster = new IdentityRosterSource(() -> new BearerTokenAuthStrategy("alice-token"),
                    registry);
            McpRepeaterRequestFactory primary = ProxiedPrimaryRequests.of(proxy,
                    Map.of("Authorization", "Bearer alice-token"));
            ScanInventory inventory = inventory(new McpDiscoveryClient(proxy.session()).discoverCatalog());
            AccessMining mining = new AccessMining(new MiningInputs(() -> Optional.of(inventory), roster,
                    () -> registry.primaryUser().id()),
                    new RoutedMiningSender(call -> primary.requestFor(call.message()), new IdentityRequests(roster),
                            proxy.routed(new VerbatimHeaderHttp())),
                    new MiningPreferences(config), store, worker, McpEventLog.noop());

            MiningReport report = mine(mining);

            System.out.println("[access-mining-e2e] " + report.summary() + " warnings=" + report.warnings());
            print("alice", store.valuesOf(alice.id()).mined());
            print("bob", store.valuesOf(bob.id()).mined());
            assertThat(report.stop()).isEqualTo(MiningStop.COMPLETED);
            assertThat(privateValues(store.valuesOf(alice.id()).mined())).isNotEmpty()
                    .doesNotContainAnyElementsOf(privateValues(store.valuesOf(bob.id()).mined()));
            assertThat(privateValues(store.valuesOf(alice.id()).mined())).contains("notes://alice/private.md");
            assertThat(privateValues(store.valuesOf(bob.id()).mined())).contains("notes://bob/private.md");
            assertThat(report.warnings()).noneMatch(line -> line.contains("another tenant"));
            assertThat(mining.minedForCurrentIdentities()).isTrue();
        } finally {
            worker.shutdownNow();
        }
    }

    private static MiningReport mine(AccessMining mining) throws Exception {
        CompletableFuture<MiningReport> finished = new CompletableFuture<>();
        mining.start(MiningListener.onFinished(finished::complete), WriteCallApproval.REFUSE).orElseThrow();
        return finished.get(Duration.ofMinutes(3).toSeconds(), TimeUnit.SECONDS);
    }

    private static ScanInventory inventory(ConnectResult catalog) {
        return new ScanInventory(catalog.tools(), catalog.resources(), catalog.resourceTemplates(), catalog.prompts());
    }

    private static List<String> privateValues(List<MinedValue> values) {
        return values.stream().filter(value -> value.scope() == ValueScope.PRIVATE).map(MinedValue::value).toList();
    }

    private static void print(String user, List<MinedValue> values) {
        values.forEach(value -> System.out.println("[access-mining-e2e] " + user + " " + value.scope() + " "
                + value.argument() + "=" + value.value() + " from " + value.source().operation() + " "
                + value.source().fieldPath()));
    }
}
