package com.mcpscanner.proxy;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.config.RedirectMode;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ResendableRequests;
import com.mcpscanner.mcp.ToolAnnotations;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.ProxyRequestStubs;
import com.mcpscanner.testutil.ScriptedProtocolTransport;
import fi.iki.elonen.NanoHTTPD;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SseProxyUpstreamTransportTest {

    private static final String ENDPOINT = "https://upstream.test/mcp";
    private static final String TOOLS_CALL =
            "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{}}}";
    private static final String SCANNER_TOOL_RESULT = "{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{\"content\":[]}}";
    private static final String ISOLATED_SESSION_ID = "isolated-session";
    private static final Duration PROXY_RESPONSE_TIMEOUT = Duration.ofMillis(300);

    private final HttpClient proxyJdkClient = mock(HttpClient.class);
    private McpScannerSession session;
    private SseProxyServer proxy;

    @AfterEach
    void tearDown() {
        if (proxy != null) proxy.stop();
        if (session != null) session.disconnect();
    }

    @Test
    void modernScannerRequestIsForwardedThroughTheInjectedTransportAndItsEventStreamReplyUnwrapped() throws Exception {
        ScriptedProtocolTransport upstream = new ScriptedProtocolTransport(request -> eventStreamReply(request,
                toolResultFor(request)));
        proxy = proxyOver(TransportType.MODERN_HTTP, upstream);

        NanoHTTPD.Response response = proxy.serve(scannerRequest(Map.of(
                ScannerSentinels.STRIP_AUTH_HEADER, "1", "Authorization", "Bearer forged", "X-Correlation", "trace")));

        assertThat(response.getStatus().getRequestStatus()).isEqualTo(200);
        assertThat(response.getMimeType()).isEqualTo("application/json");
        assertThat(bodyOf(response)).isEqualTo(SCANNER_TOOL_RESULT);
        assertThat(upstream.requests()).singleElement().satisfies(sent -> {
            assertThat(sent.uri()).isEqualTo(URI.create(ENDPOINT));
            assertThat(sent.method()).isEqualTo("POST");
            assertThat(FakeMcpServer.method(sent)).isEqualTo("tools/call");
            assertThat(header(sent, "Content-Type")).contains("application/json");
            assertThat(header(sent, "Accept")).contains("application/json, text/event-stream");
            assertThat(header(sent, "Mcp-Method")).contains("tools/call");
            assertThat(header(sent, "MCP-Protocol-Version")).contains("2026-07-28");
            assertThat(header(sent, "Authorization")).contains("Bearer forged");
            assertThat(header(sent, "X-Correlation")).contains("trace");
        });
        assertCarriesNoSentinel(upstream.requests());
        verifyNoInteractions(proxyJdkClient);
    }

    @Test
    void serverPingInForwardedEventStreamReplyIsAnsweredThroughTheInjectedTransport() throws Exception {
        String ping = "{\"jsonrpc\":\"2.0\",\"id\":\"server-ping-1\",\"method\":\"ping\"}";
        ScriptedProtocolTransport upstream = new ScriptedProtocolTransport(request ->
                "tools/call".equals(FakeMcpServer.method(request))
                        ? eventStreamReply(request, ping + "\n\nevent: message\ndata: " + toolResultFor(request))
                        : FakeMcpServer.exchange(request, 202, Map.of(), ""));
        proxy = proxyOver(TransportType.MODERN_HTTP, upstream);

        NanoHTTPD.Response response = proxy.serve(scannerRequest(Map.of(
                ScannerSentinels.STRIP_AUTH_HEADER, "1", "Authorization", "Bearer forged")));

        assertThat(bodyOf(response)).isEqualTo(SCANNER_TOOL_RESULT);
        assertThat(upstream.requests()).hasSize(2);
        assertThat(upstream.requests().get(1)).satisfies(answer -> {
            assertThat(answer.uri()).isEqualTo(URI.create(ENDPOINT));
            assertThat(answer.body()).contains("\"server-ping-1\"").contains("\"result\"");
            assertThat(header(answer, "Authorization")).contains("Bearer forged");
        });
        assertCarriesNoSentinel(upstream.requests());
        verifyNoInteractions(proxyJdkClient);
    }

    @Test
    void isolatedStreamableScannerRequestHandshakesCallsAndDeletesItsSessionThroughTheInjectedTransport()
            throws Exception {
        ScriptedProtocolTransport upstream = upstreamIssuing(ISOLATED_SESSION_ID);
        proxy = proxyOver(TransportType.STREAMABLE_HTTP, upstream);

        NanoHTTPD.Response response = proxy.serve(scannerRequest(ScannerSentinels.stripAuthOnly()));

        assertThat(response.getStatus().getRequestStatus()).isEqualTo(200);
        assertThat(bodyOf(response)).contains("\"id\":9").contains("\"result\"");
        assertThat(upstream.methods()).containsExactly("initialize", "notifications/initialized", "tools/call", null);
        assertThat(upstream.requests().get(3)).satisfies(cleanup -> {
            assertThat(cleanup.method()).isEqualTo("DELETE");
            assertThat(cleanup.uri()).isEqualTo(URI.create(ENDPOINT));
            assertThat(header(cleanup, "Mcp-Session-Id")).contains(ISOLATED_SESSION_ID);
            assertThat(header(cleanup, "MCP-Protocol-Version")).contains(FakeMcpServer.LEGACY_VERSION);
        });
        assertThat(header(upstream.requests().get(2), "Mcp-Session-Id")).contains(ISOLATED_SESSION_ID);
        assertCarriesNoSentinel(upstream.requests());
        verifyNoInteractions(proxyJdkClient);
    }

    @Test
    void isolatedStripAuthForwardNeverDeletesASessionIdIssuedToThePrimary() throws Exception {
        ScriptedProtocolTransport upstream = upstreamIssuing(FakeMcpServer.SESSION_ID);
        proxy = proxyOver(TransportType.STREAMABLE_HTTP, upstream);

        NanoHTTPD.Response response = proxy.serve(scannerRequest(ScannerSentinels.stripAuthOnly()));

        assertThat(response.getStatus().getRequestStatus()).isEqualTo(200);
        assertThat(upstream.methods()).containsExactly("initialize", "notifications/initialized", "tools/call");
        assertThat(upstream.requests()).extracting(ProtocolHttpRequest::method).doesNotContain("DELETE");
        assertCarriesNoSentinel(upstream.requests());
        verifyNoInteractions(proxyJdkClient);
    }

    @ParameterizedTest
    @EnumSource(value = TransportType.class, names = {"MODERN_HTTP", "STREAMABLE_HTTP"})
    void forwardedRequestsNeverCarryEitherScannerSentinel(TransportType transport) throws Exception {
        ScriptedProtocolTransport upstream = ScriptedProtocolTransport.fakeServer();
        proxy = proxyOver(transport, upstream);
        Map<String, String> headers = new HashMap<>(ScannerSentinels.stripAuthOnly());
        headers.putAll(proxy.auditHeaders(Map.of()));

        assertThat(proxy.serve(scannerRequest(headers)).getStatus().getRequestStatus()).isEqualTo(200);

        assertThat(upstream.requests()).isNotEmpty();
        assertCarriesNoSentinel(upstream.requests());
    }

    @Test
    void crossOriginRedirectIsNotFollowedUnderTheDefaultRedirectMode() throws Exception {
        ScriptedProtocolTransport upstream = redirectingUpstream();
        proxy = proxyOver(TransportType.MODERN_HTTP, upstream);

        NanoHTTPD.Response response = proxy.serve(scannerRequest(ScannerSentinels.stripAuthOnly()));

        assertThat(response.getStatus().getRequestStatus()).isEqualTo(307);
        assertThat(upstream.requests()).hasSize(1);
    }

    @Test
    void crossOriginRedirectIsFollowedThroughTheInjectedTransportWhenTheRedirectModeAllowsIt() throws Exception {
        ScriptedProtocolTransport upstream = redirectingUpstream();
        proxy = proxies(TransportType.MODERN_HTTP).upstreamTransport(upstream)
                .redirectMode(() -> RedirectMode.ALWAYS).build();

        NanoHTTPD.Response response = proxy.serve(scannerRequest(ScannerSentinels.stripAuthOnly()));

        assertThat(response.getStatus().getRequestStatus()).isEqualTo(200);
        assertThat(upstream.requests()).extracting(ProtocolHttpRequest::uri)
                .containsExactly(URI.create(ENDPOINT), URI.create("https://elsewhere.test/mcp"));
        verifyNoInteractions(proxyJdkClient);
    }

    @ParameterizedTest
    @EnumSource(value = TransportType.class, names = {"MODERN_HTTP", "STREAMABLE_HTTP"})
    void forwardsUpstreamWithTheProxyResponseTimeout(TransportType transport) throws Exception {
        List<Optional<Duration>> upstreamTimeouts = new CopyOnWriteArrayList<>();
        HttpClient unansweringUpstream = mock(HttpClient.class);
        when(unansweringUpstream.send(any(java.net.http.HttpRequest.class), any())).thenAnswer(call -> {
            upstreamTimeouts.add(call.<java.net.http.HttpRequest>getArgument(0).timeout());
            throw new HttpTimeoutException("request timed out");
        });
        proxy = TestSseProxies.builder(connectedSession(transport)).httpClient(unansweringUpstream)
                .responseTimeout(PROXY_RESPONSE_TIMEOUT).build();

        NanoHTTPD.Response response = proxy.serve(scannerRequest(ScannerSentinels.stripAuthOnly()));

        assertThat(response.getStatus().getRequestStatus()).isGreaterThanOrEqualTo(500);
        assertThat(upstreamTimeouts).isNotEmpty().containsOnly(Optional.of(PROXY_RESPONSE_TIMEOUT));
    }

    @ParameterizedTest
    @EnumSource(value = TransportType.class, names = {"MODERN_HTTP", "STREAMABLE_HTTP"})
    void forwardsThroughTheInjectedTransportWithTheProxyResponseTimeout(TransportType transport) throws Exception {
        ScriptedProtocolTransport upstream = ScriptedProtocolTransport.fakeServer();
        proxy = proxies(transport).upstreamTransport(upstream).responseTimeout(PROXY_RESPONSE_TIMEOUT).build();

        NanoHTTPD.Response response = proxy.serve(scannerRequest(ScannerSentinels.stripAuthOnly()));

        assertThat(response.getStatus().getRequestStatus()).isEqualTo(200);
        assertThat(upstream.timeouts()).isNotEmpty().containsOnly(PROXY_RESPONSE_TIMEOUT);
    }

    @Test
    void aReadOnlyToolCallWhoseConnectionTheServerClosedBeforeReplyingIsResentOnce() throws Exception {
        ScriptedProtocolTransport upstream = closingTheFirstConnection();
        ResendableRequests resendable = new ResendableRequests();
        resendable.learn(List.of(new McpToolDefinition("echo", "", "{}", List.of(),
                new ToolAnnotations(null, true, null, null, null))));
        proxy = proxies(TransportType.MODERN_HTTP).upstreamTransport(upstream).resendableRequests(resendable).build();

        NanoHTTPD.Response response = proxy.serve(scannerRequest(ScannerSentinels.stripAuthOnly()));

        assertThat(response.getStatus().getRequestStatus()).isEqualTo(200);
        assertThat(upstream.methods()).containsExactly("tools/call", "tools/call");
    }

    @Test
    void aToolCallNotKnownToBeReadOnlyIsNeverResent() throws Exception {
        ScriptedProtocolTransport upstream = closingTheFirstConnection();
        proxy = proxyOver(TransportType.MODERN_HTTP, upstream);

        NanoHTTPD.Response response = proxy.serve(scannerRequest(ScannerSentinels.stripAuthOnly()));

        assertThat(response.getStatus().getRequestStatus()).isEqualTo(502);
        assertThat(upstream.methods()).containsExactly("tools/call");
    }

    private static ScriptedProtocolTransport closingTheFirstConnection() {
        AtomicBoolean closed = new AtomicBoolean();
        return new ScriptedProtocolTransport(request -> {
            if (closed.compareAndSet(false, true)) {
                throw new ScriptedProtocolTransport.ScriptedIoFailure(
                        new IOException("HTTP/1.1 header parser received no bytes"));
            }
            return FakeMcpServer.respond(request);
        });
    }

    private SseProxyServer proxyOver(TransportType transport, ScriptedProtocolTransport upstream) {
        return proxies(transport).upstreamTransport(upstream).build();
    }

    private TestSseProxies.Builder proxies(TransportType transport) {
        return TestSseProxies.builder(connectedSession(transport)).httpClient(proxyJdkClient);
    }

    private McpScannerSession connectedSession(TransportType transport) {
        session = new McpScannerSession(mock(HttpClient.class), ScriptedProtocolTransport.fakeServer(),
                McpEventLog.noop(), () -> true, ClientIdentityProvider.defaults());
        session.connect(new McpServerConfig(ENDPOINT, transport, new NoAuthStrategy()));
        return session;
    }

    private static ScriptedProtocolTransport upstreamIssuing(String sessionId) {
        return new ScriptedProtocolTransport(request -> "initialize".equals(FakeMcpServer.method(request))
                ? FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("application/json"),
                        "Mcp-Session-Id", List.of(sessionId)), FakeMcpServer.initializeReply(request))
                : FakeMcpServer.respond(request));
    }

    private static ScriptedProtocolTransport redirectingUpstream() {
        return new ScriptedProtocolTransport(request -> request.uri().getHost().equals("upstream.test")
                ? FakeMcpServer.exchange(request, 307, Map.of("Location", List.of("https://elsewhere.test/mcp")), "")
                : FakeMcpServer.respond(request));
    }

    private static String toolResultFor(ProtocolHttpRequest request) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + idOf(request) + ",\"result\":{\"content\":[]}}";
    }

    private static long idOf(ProtocolHttpRequest request) {
        try {
            return McpObjectMapper.INSTANCE.readTree(request.body()).path("id").asLong();
        } catch (IOException invalid) {
            throw new UncheckedIOException(invalid);
        }
    }

    private static ProtocolHttpResponse eventStreamReply(ProtocolHttpRequest request, String json) {
        return FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("text/event-stream")),
                "event: message\ndata: " + json + "\n\n");
    }

    private static Optional<String> header(ProtocolHttpRequest request, String name) {
        return request.headers().entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst();
    }

    private static void assertCarriesNoSentinel(List<ProtocolHttpRequest> requests) {
        assertThat(requests).allSatisfy(sent -> {
            assertThat(header(sent, ScannerSentinels.STRIP_AUTH_HEADER)).isEmpty();
            assertThat(header(sent, ScannerSentinels.AUDIT_CAPABILITY_HEADER)).isEmpty();
        });
    }

    private static String bodyOf(NanoHTTPD.Response response) throws Exception {
        return new String(response.getData().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static NanoHTTPD.IHTTPSession scannerRequest(Map<String, String> headers) {
        NanoHTTPD.IHTTPSession request = mock(NanoHTTPD.IHTTPSession.class);
        when(request.getMethod()).thenReturn(NanoHTTPD.Method.POST);
        when(request.getUri()).thenReturn("/mcp");
        ProxyRequestStubs.stubBody(request, headers, TOOLS_CALL);
        return request;
    }
}
