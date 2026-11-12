package com.mcpscanner.client;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.internal.ObjectFactoryLocator;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.scan.request.ClientRegistrationRequestFactory;
import com.mcpscanner.scan.request.McpHttpRequestFactory;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ScanRequestRenderer;
import com.mcpscanner.scan.McpRepeaterRequestFactory;
import com.mcpscanner.scan.ScanSession;
import com.mcpscanner.scan.ScanTarget;
import com.mcpscanner.scan.ScanConnection;
import com.mcpscanner.scan.SendToConnectionPolicy;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SendToHandoffHeadersTest {

    private static final String SESSION_ID = "session-2025-06-18";

    private final AtomicBoolean handleAuthentication = new AtomicBoolean();
    private McpFixtureServer upstream;
    private McpScannerSession session;
    private String lastRawRequest;

    @BeforeEach
    void start() throws Exception {
        MontoyaTestFactory.install();
        when(ObjectFactoryLocator.FACTORY.httpRequest(any(HttpService.class), anyString()))
                .thenAnswer(call -> {
                    lastRawRequest = call.getArgument(1);
                    return trackedRequest(parseHeaders(lastRawRequest));
                });
        upstream = new McpFixtureServer(McpFixtureProfile.streamable("2025-06-18"));
        session = new McpScannerSession(HttpClient.newHttpClient(), new McpEventLog(null));
    }

    @AfterEach
    void stop() {
        session.disconnect();
        upstream.close();
    }

    @Test
    void unhandledAuthenticationWritesEachCredentialAndSessionHeaderOnceWithoutCapability() {
        connect(new BearerTokenAuthStrategy("live-token"));

        List<String[]> headers = handoffHeaders();

        assertThat(values(headers, "Authorization")).containsExactly("Bearer live-token");
        assertThat(values(headers, "Mcp-Session-Id")).containsExactly(SESSION_ID);
        assertThat(values(headers, "MCP-Protocol-Version")).containsExactly("2025-06-18");
        assertThat(values(headers, ScannerSentinels.AUDIT_CAPABILITY_HEADER)).isEmpty();
    }

    @Test
    void unhandledAuthenticationRequestIsAcceptedWhenSentStraightToTheServer() throws Exception {
        connect(new BearerTokenAuthStrategy("live-token"));
        List<String[]> headers = handoffHeaders();
        java.net.http.HttpRequest.Builder direct = java.net.http.HttpRequest.newBuilder(java.net.URI.create(upstream.endpoint()))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        lastRawRequest.substring(lastRawRequest.indexOf("\r\n\r\n") + 4)));
        headers.stream().filter(header -> !List.of("host", "content-length").contains(header[0].toLowerCase(Locale.ROOT)))
                .forEach(header -> direct.header(header[0], header[1]));

        var response = HttpClient.newHttpClient().send(direct.build(), java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(upstream.callsTo("tools/call")).singleElement().satisfies(call -> {
            assertThat(call.sessionId()).isEqualTo(SESSION_ID);
            assertThat(call.sessionHeaderCount()).isEqualTo(1);
            assertThat(call.scannerCapabilityReceived()).isFalse();
        });
    }

    @Test
    void unhandledAuthenticationWritesCustomAuthHeadersOnce() {
        connect(new CustomHeaderAuthStrategy(Map.of("X-Api-Key", "key-1")));

        List<String[]> headers = handoffHeaders();

        assertThat(values(headers, "X-Api-Key")).containsExactly("key-1");
        assertThat(values(headers, "Mcp-Session-Id")).containsExactly(SESSION_ID);
        assertThat(values(headers, ScannerSentinels.AUDIT_CAPABILITY_HEADER)).isEmpty();
    }

    @Test
    void handledAuthenticationAddsOneCapabilityHeaderAndLeavesAuthorizationToTheProxy() {
        handleAuthentication.set(true);
        connect(new BearerTokenAuthStrategy("live-token"));

        List<String[]> headers = handoffHeaders();

        assertThat(values(headers, ScannerSentinels.AUDIT_CAPABILITY_HEADER)).containsExactly("signed");
        assertThat(values(headers, "Authorization")).isEmpty();
        assertThat(values(headers, "Mcp-Session-Id")).containsExactly(SESSION_ID);
    }

    private void connect(AuthStrategy auth) {
        session.connect(new McpServerConfig(upstream.endpoint(), TransportType.STREAMABLE_HTTP, auth));
    }

    private List<String[]> handoffHeaders() {
        SendToConnectionPolicy policy = new SendToConnectionPolicy(handleAuthentication::get,
                () -> ScanConnection.unbound(baseline -> Map.of(ScannerSentinels.AUDIT_CAPABILITY_HEADER, "signed")),
                session::scannerHeaders);
        McpRepeaterRequestFactory factory = new McpRepeaterRequestFactory(
                ScanSession.of(session::resolvedEndpoint, session::scannerHeaders),
                RequestGenerator.standard(), new ScanRequestRenderer(new McpHttpRequestFactory(session::nextRequestId, session)::build,
                        new ClientRegistrationRequestFactory()), policy);
        HttpRequest request = factory.requestFor(
                new ScanTarget.Tool(new McpToolDefinition("echo", "", "{\"type\":\"object\"}")));
        return request.headers().stream().map(header -> new String[]{header.name(), header.value()}).toList();
    }

    private static List<String> values(List<String[]> headers, String name) {
        return headers.stream().filter(header -> header[0].equalsIgnoreCase(name)).map(header -> header[1]).toList();
    }

    private static List<String[]> parseHeaders(String raw) {
        String head = raw.substring(0, raw.indexOf("\r\n\r\n"));
        return head.lines().skip(1).map(line -> line.split(": ", 2)).toList();
    }

    private static HttpRequest trackedRequest(List<String[]> headers) {
        HttpRequest request = mock(HttpRequest.class);
        List<HttpHeader> headerMocks = headers.stream().map(SendToHandoffHeadersTest::header).toList();
        when(request.headers()).thenReturn(headerMocks);
        when(request.withAddedHeader(anyString(), anyString())).thenAnswer(call -> {
            List<String[]> next = new ArrayList<>(headers);
            next.add(new String[]{call.getArgument(0), call.getArgument(1)});
            return trackedRequest(next);
        });
        when(request.withUpdatedHeader(anyString(), anyString())).thenAnswer(call -> {
            String name = call.<String>getArgument(0).toLowerCase(Locale.ROOT);
            List<String[]> next = new ArrayList<>(headers.stream()
                    .map(header -> header[0].toLowerCase(Locale.ROOT).equals(name)
                            ? new String[]{header[0], call.getArgument(1)} : header).toList());
            return trackedRequest(next);
        });
        return request;
    }

    private static HttpHeader header(String[] pair) {
        HttpHeader header = mock(HttpHeader.class);
        when(header.name()).thenReturn(pair[0]);
        when(header.value()).thenReturn(pair[1]);
        return header;
    }
}
