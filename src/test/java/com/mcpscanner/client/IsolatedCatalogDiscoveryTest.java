package com.mcpscanner.client;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class IsolatedCatalogDiscoveryTest {

    private static final String ALICE = "Bearer alice";
    private static final String BOB = "Bearer bob";
    private static final String BOB_ONLY_TOOL = "bob_only";

    private final JdkProtocolHttpTransport sharedTransport = new JdkProtocolHttpTransport(McpHttpClients.upstream());
    private final McpClientManager primary = new McpClientManager(McpEventLog.noop(),
            ClientIdentityProvider.defaults(), sharedTransport);
    private final IsolatedCatalogDiscovery discovery = new IsolatedCatalogDiscovery(McpEventLog.noop(),
            ClientIdentityProvider.defaults(), sharedTransport, ScannerSessionSettings.defaults());
    private McpFixtureServer server;

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void shutDown() {
        primary.disconnect();
        if (server != null) {
            server.close();
        }
    }

    static Stream<Arguments> transports() {
        return Stream.of(
                Arguments.of(TransportType.MODERN_HTTP, McpFixtureProfile.modernOnly()),
                Arguments.of(TransportType.STREAMABLE_HTTP, McpFixtureProfile.streamable("2025-06-18")),
                Arguments.of(TransportType.SSE, McpFixtureProfile.sseOnly("2024-11-05")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transports")
    void listsTheCatalogTheIdentityCredentialIsShown(TransportType transport, McpFixtureProfile profile)
            throws IOException {
        startServer(profile);
        primary.connect(config(transport, "alice"));

        ConnectResult listing = discovery.discover(config(transport, "bob"));

        assertThat(listing.tools()).extracting(McpToolDefinition::name)
                .containsExactlyInAnyOrder(McpFixtureServer.toolName(), BOB_ONLY_TOOL);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transports")
    void leavesThePrimarySessionConnectedWithItsOwnCredentials(TransportType transport, McpFixtureProfile profile)
            throws IOException {
        startServer(profile);
        primary.connect(config(transport, "alice"));
        long generation = primary.scannerSession().connectionGeneration();

        discovery.discover(config(transport, "bob"));

        assertThat(primary.isConnected()).isTrue();
        assertThat(primary.scannerSession().connectionGeneration()).isEqualTo(generation);
        assertThat(primary.scannerSession().scannerHeaders()).containsEntry("Authorization", ALICE);
        assertThat(primary.scannerSession().catalog("tools/list", null).error()).isNull();
        assertThat(server.callsTo("tools/list")).extracting(McpFixtureServer.ObservedCall::authorization)
                .containsExactly(ALICE, BOB, ALICE);
    }

    @Test
    void endsOnlyTheIdentitySessionOnInitializeBasedHttp() throws IOException {
        startServer(McpFixtureProfile.streamable("2025-06-18"));
        primary.connect(config(TransportType.STREAMABLE_HTTP, "alice"));

        discovery.discover(config(TransportType.STREAMABLE_HTTP, "bob"));

        assertThat(server.sessionDeleteAuthorizations()).containsExactly(BOB);
    }

    @Test
    void failureIsReportedWithoutDisturbingThePrimary() throws IOException {
        startServer(McpFixtureProfile.modernOnly());
        primary.connect(config(TransportType.MODERN_HTTP, "alice"));

        assertThatThrownBy(() -> discovery.discover(new McpServerConfig(closedEndpoint(), TransportType.MODERN_HTTP,
                new BearerTokenAuthStrategy("bob")))).isInstanceOf(RuntimeException.class);

        assertThat(primary.isConnected()).isTrue();
        assertThat(primary.scannerSession().scannerHeaders()).containsEntry("Authorization", ALICE);
    }

    @Test
    void connectListenersLearnTheResolvedTransportAndCatalog() throws IOException {
        startServer(McpFixtureProfile.streamable("2025-06-18"));
        java.util.List<PrimaryConnection> connections = new java.util.ArrayList<>();
        primary.addConnectListener(connections::add);

        primary.connect(config(TransportType.AUTO, "alice"));

        assertThat(connections).singleElement().satisfies(connection -> {
            assertThat(connection.endpoint()).isEqualTo(server.endpoint());
            assertThat(connection.transport()).isEqualTo(TransportType.STREAMABLE_HTTP);
            assertThat(connection.catalog().tools()).extracting(McpToolDefinition::name)
                    .containsExactly(McpFixtureServer.toolName());
        });
    }

    private void startServer(McpFixtureProfile profile) throws IOException {
        server = new McpFixtureServer(profile);
        server.grantToolTo(BOB, BOB_ONLY_TOOL);
    }

    private McpServerConfig config(TransportType transport, String token) {
        return new McpServerConfig(server.endpoint(), transport, new BearerTokenAuthStrategy(token));
    }

    private static String closedEndpoint() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort() + "/mcp";
        }
    }
}
