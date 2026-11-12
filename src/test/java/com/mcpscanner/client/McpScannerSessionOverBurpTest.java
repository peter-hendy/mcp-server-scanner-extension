package com.mcpscanner.client;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.proxy.Http1LoopbackRelay;
import com.mcpscanner.proxy.LoopbackRoutingKey;
import com.mcpscanner.proxy.McpHttpHandler;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.ScriptedBurpHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.mcpscanner.testutil.ScannerRequestToBeSent.sentinelCarryingScannerRequestTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpScannerSessionOverBurpTest {

    private static final String ENDPOINT = "https://mcp.example.test/mcp";
    private static final Duration CONNECT_BOUND = Duration.ofSeconds(10);
    private static final Duration SHORT_RESPONSE_TIMEOUT = Duration.ofMillis(300);

    private final McpEventLog eventLog = new McpEventLog(null);
    private final SseProxyServer proxy = mock(SseProxyServer.class);
    private McpHttpHandler handler;

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
        when(proxy.routingKey()).thenReturn(LoopbackRoutingKey.mint());
    }

    @AfterEach
    void shutDown() {
        eventLog.shutdown();
    }

    @ParameterizedTest
    @EnumSource(value = TransportType.class, names = {"STREAMABLE_HTTP", "MODERN_HTTP"})
    void connectAndCatalogCompleteWhileBurpRunsTheRealHandlerOnAnotherThread(TransportType transport) {
        McpScannerSession session = sessionThrough(new ScriptedBurpHttp(this::replyAfterTheHandlerRoutesARequest),
                McpScannerSession.DEFAULT_REQUEST_TIMEOUT);
        handler = new McpHttpHandler(session, proxy, new Http1LoopbackRelay(mock(Http.class)));

        assertTimeoutPreemptively(CONNECT_BOUND, () -> {
            session.connect(config(transport));
            session.catalog("tools/list", null);
        });

        assertThat(session.transportType()).isEqualTo(transport);
        verify(proxy, atLeastOnce()).port();
    }

    @Test
    void noResponseFromBurpWithinTheResponseTimeoutFailsConnectWithTheBurpNoResponseError() {
        McpScannerSession session = sessionThrough(new ScriptedBurpHttp(burpGetsNoReply -> Optional.empty()),
                SHORT_RESPONSE_TIMEOUT);

        assertTimeoutPreemptively(CONNECT_BOUND, () ->
                assertThatThrownBy(() -> session.connect(config(TransportType.STREAMABLE_HTTP)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("No response from " + ENDPOINT + " through Burp"));
    }

    private Optional<ProtocolHttpResponse> replyAfterTheHandlerRoutesARequest(ProtocolHttpRequest request) {
        HttpRequestToBeSent scannerRequest = sentinelCarryingScannerRequestTo(request.uri());
        CompletableFuture.runAsync(() -> handler.handleHttpRequestToBeSent(scannerRequest)).join();
        return Optional.of(FakeMcpServer.respond(request));
    }

    private McpScannerSession sessionThrough(ScriptedBurpHttp burpHttp, Duration responseTimeout) {
        return new McpScannerSession(mock(HttpClient.class), new BurpProtocolHttpTransport(burpHttp),
                eventLog, () -> false, responseTimeout, ClientIdentityProvider.defaults());
    }

    private static McpServerConfig config(TransportType transport) {
        return new McpServerConfig(ENDPOINT, transport, new NoAuthStrategy());
    }
}
