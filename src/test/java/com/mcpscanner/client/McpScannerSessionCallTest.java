package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.ScriptedProtocolTransport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class McpScannerSessionCallTest {

    private static final String ENDPOINT = "http://localhost:8080/mcp";
    private static final String COMPLETION_PARAMS =
            "{\"ref\":{\"type\":\"ref/prompt\",\"name\":\"p\"},\"argument\":{\"name\":\"a\",\"value\":\"py\"}}";

    private final McpEventLog eventLog = new McpEventLog(null);

    @Test
    void callSendsTheMethodWithItsParamsOnTheLiveSessionAndReturnsTheReply() {
        ScriptedProtocolTransport transport = ScriptedProtocolTransport.fakeServer();
        McpScannerSession session = session(transport);
        session.connect(config(ENDPOINT, TransportType.STREAMABLE_HTTP));

        var reply = session.call("completion/complete", COMPLETION_PARAMS);

        assertThat(reply.error()).isNullOrEmpty();
        ProtocolHttpRequest sent = transport.requests().stream()
                .filter(request -> "completion/complete".equals(FakeMcpServer.method(request)))
                .reduce((first, second) -> second).orElseThrow();
        assertThat(sent.body()).contains("\"ref/prompt\"").contains("\"value\":\"py\"");
        assertThat(sent.headers()).containsEntry("MCP-Session-Id", FakeMcpServer.SESSION_ID);
    }

    @Test
    void callFailsWithTheHttpStatusOfARefusedReply() {
        ScriptedProtocolTransport refusing = new ScriptedProtocolTransport(request ->
                "completion/complete".equals(FakeMcpServer.method(request))
                        ? FakeMcpServer.exchange(request, 500, Map.of(), "")
                        : FakeMcpServer.respond(request));
        McpScannerSession session = session(refusing);
        session.connect(config(ENDPOINT, TransportType.MODERN_HTTP));

        assertThatThrownBy(() -> session.call("completion/complete", COMPLETION_PARAMS))
                .isInstanceOfSatisfying(McpHttpStatusException.class,
                        failure -> assertThat(failure.status()).isEqualTo(500));
    }

    @Test
    void callIsRefusedWhenDisconnected() {
        McpScannerSession session = session(ScriptedProtocolTransport.fakeServer());

        assertThatThrownBy(() -> session.call("completion/complete", COMPLETION_PARAMS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disconnected");
    }

    @Test
    void callTravelsOverTheSseStream() throws IOException {
        try (McpFixtureServer server = new McpFixtureServer(McpFixtureProfile.sseOnly("2024-11-05"))) {
            McpScannerSession session = new McpScannerSession(HttpClient.newHttpClient(),
                    ScriptedProtocolTransport.fakeServer(), eventLog, () -> true, ClientIdentityProvider.defaults());
            try {
                session.connect(config(server.endpoint(), TransportType.SSE));

                var reply = session.call("completion/complete", COMPLETION_PARAMS);

                assertThat(reply.result()).containsKey("completion");
                assertThat(server.callsTo("completion/complete")).singleElement()
                        .satisfies(call -> assertThat(call.channel()).isEqualTo("sse"));
            } finally {
                session.disconnect();
            }
        }
    }

    private McpScannerSession session(ProtocolHttpTransport transport) {
        return new McpScannerSession(mock(HttpClient.class), transport, eventLog, () -> true,
                ClientIdentityProvider.defaults());
    }

    private static McpServerConfig config(String endpoint, TransportType transport) {
        return new McpServerConfig(endpoint, transport, new NoAuthStrategy());
    }
}
