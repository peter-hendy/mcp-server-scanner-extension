package com.mcpscanner.client;

import burp.api.montoya.logging.Logging;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.proxy.TestSseProxies;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Timeout(30)
class SseNegotiatedVersionProxyTest {
    private static final String NEGOTIATED_VERSION = "2025-06-18";

    private final HttpClient client = HttpClient.newHttpClient();
    private McpFixtureServer upstream;
    private McpScannerSession session;
    private SseProxyServer proxy;

    @BeforeEach
    void connectThroughProxy() throws IOException {
        MontoyaTestFactory.install();
        upstream = new McpFixtureServer(
                McpFixtureProfile.sseOnly(NEGOTIATED_VERSION).requiringSseVersionHeader());
        session = new McpScannerSession(client, new McpEventLog(mock(Logging.class)));
        session.connect(new McpServerConfig(upstream.endpoint(), TransportType.SSE, new NoAuthStrategy()));
        proxy = TestSseProxies.builder(session).httpClient(HttpClient.newHttpClient()).build();
        proxy.start();
    }

    @AfterEach
    void shutDown() {
        proxy.stop();
        session.disconnect();
        upstream.close();
    }

    @Test
    void scanRequestRelayedToSseServerCarriesTheNegotiatedVersion() throws Exception {
        McpRequests.Request scanRequest = session.operation("tools/call",
                "{\"name\":\"" + McpFixtureServer.toolName() + "\",\"arguments\":{}}", session.nextRequestId());

        HttpResponse<String> response = sendThroughProxy(scanRequest);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(upstream.callsTo("tools/call")).singleElement()
                .extracting(McpFixtureServer.ObservedCall::protocolVersion).isEqualTo(NEGOTIATED_VERSION);
    }

    private HttpResponse<String> sendThroughProxy(McpRequests.Request scanRequest) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + proxy.port() + "/messages"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(scanRequest.body()));
        scanRequest.headers().forEach(request::header);
        proxy.auditHeaders(scanRequest.headers()).forEach(request::header);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
