package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.testutil.CausalChain;
import com.mcpscanner.testutil.ClientCertificateBurpHttp;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.http.HttpClient;

import static com.mcpscanner.testutil.MutualTlsFixtures.burpTransport;
import static com.mcpscanner.testutil.MutualTlsFixtures.managerThrough;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(60)
class BurpStreamedReplyConnectTest {

    private static final String STREAMABLE_VERSION = "2025-06-18";

    private final McpEventLog eventLog = new McpEventLog(null);
    private McpFixtureServer server;
    private McpClientManager manager;

    @BeforeEach
    void startServerStreamingItsReplies() throws Exception {
        MontoyaTestFactory.install();
        server = new McpFixtureServer(McpFixtureProfile.streamable(STREAMABLE_VERSION)
                .withFraming(McpFixtureProfile.ReplyFraming.EVENT_STREAM));
    }

    @AfterEach
    void shutDown() {
        if (manager != null) {
            manager.disconnect();
        }
        server.close();
        eventLog.shutdown();
    }

    @Test
    void connectThroughBurpFailsWithTheStreamingErrorWhenTheServerStreamsItsReply() throws Exception {
        manager = managerThrough(eventLog, burpTransport(ClientCertificateBurpHttp.withoutClientCertificate()));

        assertThatThrownBy(() -> manager.connect(config()))
                .satisfies(failure -> assertThat(BurpStreamingResponseException.caused(failure)).isTrue())
                .satisfies(failure -> assertThat(CausalChain.of(failure))
                        .noneMatch(cause -> ClientCertificateBurpHttp.STREAMING_REFUSAL.equals(cause.getMessage())));
        assertThat(server.callsTo("initialize")).hasSize(1);
    }

    @Test
    void connectWithBurpNetworkingOffReadsTheStreamedReply() {
        manager = managerThrough(eventLog, new JdkProtocolHttpTransport(HttpClient.newHttpClient()));

        ConnectResult result = manager.connect(config());

        assertThat(result.tools()).extracting(McpToolDefinition::name).containsExactly(McpFixtureServer.toolName());
    }

    private McpServerConfig config() {
        return new McpServerConfig(server.endpoint(), TransportType.STREAMABLE_HTTP, new NoAuthStrategy());
    }
}
