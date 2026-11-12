package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.proxy.SseProxyServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ManagedRepeaterProxyTest {

    @Test
    void sessionBearingResourceRequestReachesUpstreamThroughManagedProxy() throws Exception {
        try (McpFixtureServer upstream = new McpFixtureServer(McpFixtureProfile.streamable("2025-06-18"))) {
            HttpClient client = HttpClient.newHttpClient();
            McpScannerSession session = new McpScannerSession(client, new McpEventLog(null));
            session.connect(new McpServerConfig(upstream.endpoint(), TransportType.STREAMABLE_HTTP,
                    new NoAuthStrategy()));
            SseProxyServer proxy = com.mcpscanner.proxy.TestSseProxies.builder(session).build();
            proxy.start();
            try {
                String body = "{\"jsonrpc\":\"2.0\",\"id\":17,\"method\":\"resources/read\",\"params\":{\"uri\":\"ui://widget/site-builder.html\"}}";
                Map<String, String> baseline = Map.of("Mcp-Session-Id", "session-2025-06-18",
                        "MCP-Protocol-Version", "2025-06-18");
                HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + proxy.port() + "/mcp"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                baseline.forEach(request::header);
                proxy.auditHeaders(baseline).forEach(request::header);

                HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(upstream.callsTo("resources/read")).singleElement().satisfies(call -> {
                    assertThat(call.sessionId()).isEqualTo("session-2025-06-18");
                    assertThat(call.sessionHeaderCount()).isEqualTo(1);
                    assertThat(call.scannerCapabilityReceived()).isFalse();
                    assertThat(call.protocolVersion()).isEqualTo("2025-06-18");
                });
            } finally {
                proxy.stop();
                session.disconnect();
            }
        }
    }
}
