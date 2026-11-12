package com.mcpscanner.performance;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.McpDiscoveryClient;
import com.mcpscanner.client.McpFixtureProfile;
import com.mcpscanner.client.McpFixtureServer;
import com.mcpscanner.client.McpHttpClients;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectRequestBudgetTest {

    private static final String[] LEGACY_VERSIONS = {"2025-11-25", "2025-06-18", "2025-03-26"};
    private static final String SSE_VERSION = "2024-11-05";

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void connectOverModernHttp() throws Exception {
        assertConnectWithinBudget("Connect, modern HTTP", McpFixtureProfile.modernOnly(), TransportType.MODERN_HTTP);
    }

    @Test
    void connectOverInitializeBasedHttp() throws Exception {
        assertConnectWithinBudget("Connect, initialize-based HTTP", McpFixtureProfile.streamable(LEGACY_VERSIONS),
                TransportType.STREAMABLE_HTTP);
    }

    @Test
    void connectOverSse() throws Exception {
        assertConnectWithinBudget("Connect, SSE", McpFixtureProfile.sseOnly(SSE_VERSION), TransportType.SSE);
    }

    @Test
    void discoveryFollowsEveryPageOnce() throws Exception {
        try (McpFixtureServer server = new McpFixtureServer(
                McpFixtureProfile.streamable(LEGACY_VERSIONS).pagingTools(10, 3))) {
            McpScannerSession session = connected(server, TransportType.STREAMABLE_HTTP);
            try {
                server.forgetRequests();
                assertThat(new McpDiscoveryClient(session).discoverCatalog().tools()).hasSize(10);
                RequestBudgets.assertWithin("Discovery, 10 tools in pages of 3", server.requests());
            } finally {
                session.disconnect();
            }
        }
    }

    private static void assertConnectWithinBudget(String operation, McpFixtureProfile profile,
                                                  TransportType transport) throws Exception {
        try (McpFixtureServer server = new McpFixtureServer(profile)) {
            McpScannerSession session = connected(server, transport);
            try {
                RequestBudgets.assertWithin(operation, server.requests());
            } finally {
                session.disconnect();
            }
        }
    }

    private static McpScannerSession connected(McpFixtureServer server, TransportType transport) {
        McpScannerSession session = new McpScannerSession(McpHttpClients.upstream(), McpEventLog.noop());
        session.connect(new McpServerConfig(server.endpoint(), transport, new NoAuthStrategy()));
        return session;
    }
}
