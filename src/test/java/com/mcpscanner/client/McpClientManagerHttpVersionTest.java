package com.mcpscanner.client;

import burp.api.montoya.logging.Logging;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;

class McpClientManagerHttpVersionTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void connectsToAServerThatNeverAnswersAnH2cUpgrade() throws Exception {
        try (H2cStallingMcpServer server = new H2cStallingMcpServer()) {
            McpClientManager manager = new McpClientManager(McpEventLog.noop());
            try {
                ConnectResult result = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> manager.connect(
                        new McpServerConfig(server.endpoint(), TransportType.STREAMABLE_HTTP, new NoAuthStrategy())));

                assertThat(result.serverMetadata()).isNotNull();
                assertThat(manager.scannerSession().scannerHeaders()).containsEntry("Mcp-Session-Id", "h2c-stalling");
            } finally {
                manager.disconnect();
            }
        }
    }
}
