package com.mcpscanner.integration;

import burp.api.montoya.logging.Logging;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
@ExtendWith(MockitoExtension.class)
class StreamableHandshakeIT {

    private static final String HANDSHAKE_ERA_ONLY = "2025-11-25";
    private static final String STATELESS_ONLY = "2026-07-28";

    @Mock
    private Logging logging;

    @BeforeAll
    static void installFactory() {
        MontoyaTestFactory.install();
    }

    @Test
    void noAuthHandshakeEstablishesRealSession() throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(
                "--protocol-version", HANDSHAKE_ERA_ONLY)) {
            McpScannerSession session = newSession();
            String endpoint = McpCheckItSupport.mcpEndpoint(server.port());

            session.connect(new McpServerConfig(endpoint, TransportType.STREAMABLE_HTTP, new NoAuthStrategy()));

            assertThat(session.resolvedEndpoint())
                    .as("handshake resolves the streamable endpoint as-is")
                    .isEqualTo(endpoint);
            assertThat(session.scannerHeaders())
                    .as("server-minted Mcp-Session-Id is captured via the JDK HttpClient handshake")
                    .containsKey("Mcp-Session-Id");
            assertThat(session.scannerHeaders().get("Mcp-Session-Id"))
                    .as("session id is a non-blank server value")
                    .isNotBlank();
            assertThat(session.scannerHeaders())
                    .as("a protocol version is negotiated from the initialize reply")
                    .containsKey("MCP-Protocol-Version");
            assertThat(session.scannerHeaders().get("MCP-Protocol-Version")).isEqualTo(HANDSHAKE_ERA_ONLY);

            session.disconnect();
        }
    }

    @Test
    void statelessServerIsAdoptedWithoutHandshakeOrSessionId() throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(
                "--protocol-version", STATELESS_ONLY)) {
            McpScannerSession session = newSession();

            session.connect(new McpServerConfig(McpCheckItSupport.mcpEndpoint(server.port()),
                    TransportType.AUTO, new NoAuthStrategy()));

            assertThat(session.scannerHeaders())
                    .doesNotContainKey("Mcp-Session-Id")
                    .containsEntry("MCP-Protocol-Version", STATELESS_ONLY);

            session.disconnect();
        }
    }

    @Test
    void authenticatedHandshakeSucceedsWithMintedToken() throws Exception {
        try (McpCheckItSupport.RunningServer server =
                     McpCheckItSupport.startServer(
                             "--default-mode", "safe", "--protocol-version", HANDSHAKE_ERA_ONLY)) {
            String token = McpCheckItSupport.mintTestToken(server.port());
            McpScannerSession session = newSession();
            String endpoint = McpCheckItSupport.mcpEndpoint(server.port());

            session.connect(new McpServerConfig(
                    endpoint, TransportType.STREAMABLE_HTTP, new BearerTokenAuthStrategy(token)));

            assertThat(session.scannerHeaders())
                    .as("authenticated handshake captures a real session id")
                    .containsKey("Mcp-Session-Id");
            assertThat(session.scannerHeaders().get("Mcp-Session-Id")).isNotBlank();
            assertThat(session.scannerHeaders())
                    .containsEntry("Authorization", "Bearer " + token)
                    .containsKey("MCP-Protocol-Version");

            session.disconnect();
        }
    }

    private McpScannerSession newSession() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new McpScannerSession(httpClient, new McpEventLog(null));
    }
}
