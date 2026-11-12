package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.shared.stream.ScriptedEventStreams;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.client.McpClientManager;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.requests.SendOnlyHttp;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.proxy.Http1LoopbackRelay;
import com.mcpscanner.proxy.McpHttpHandler;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.proxy.TestSseProxies;
import com.mcpscanner.testutil.LegacySseMcpServer;
import com.mcpscanner.testutil.LegacySseMcpServer.Guard;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.toolCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UnauthenticatedRequestExecutionSseTest {

    private static final BearerTokenAuthStrategy BEARER = new BearerTokenAuthStrategy("valid-token");

    private LegacySseMcpServer server;
    private McpClientManager manager;
    private SseProxyServer proxy;

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void tearDown() {
        if (proxy != null) proxy.stop();
        if (manager != null) manager.disconnect();
        if (server != null) server.close();
    }

    @Test
    void anonymousStreamThatRunsTheOperationIsReported() throws Exception {
        List<AuditIssue> issues = auditToolCallAgainst(Guard.OPEN);

        assertThat(issues).singleElement()
                .satisfies(issue -> assertThat(issue.name()).isEqualTo(FindingKind.ANONYMOUS_EXECUTION.issueName()));
        assertThat(server.calls()).anySatisfy(call -> {
            assertThat(call.path()).isEqualTo("/sse");
            assertThat(call.authorized()).isFalse();
            assertThat(call.status()).isEqualTo(200);
        });
        assertThat(server.calls()).anySatisfy(call -> {
            assertThat(call.rpcMethod()).isEqualTo("tools/call");
            assertThat(call.authorized()).isFalse();
            assertThat(call.status()).isEqualTo(202);
        });
    }

    @Test
    void refusedAnonymousStreamIsSilentAndNoAnonymousProbeReachesAMessageEndpoint() throws Exception {
        List<AuditIssue> issues = auditToolCallAgainst(Guard.STREAM);

        assertThat(issues).isEmpty();
        assertThat(server.calls()).anySatisfy(call -> {
            assertThat(call.path()).isEqualTo("/sse");
            assertThat(call.status()).isEqualTo(401);
        });
        assertThat(server.calls()).noneSatisfy(call -> {
            assertThat(call.path()).isEqualTo("/messages");
            assertThat(call.authorized()).isFalse();
        });
    }

    @Test
    void anonymousStreamWhoseMessagesAreRefusedIsSilent() throws Exception {
        List<AuditIssue> issues = auditToolCallAgainst(Guard.MESSAGE);

        assertThat(issues).isEmpty();
        assertThat(server.calls()).anySatisfy(call -> {
            assertThat(call.path()).isEqualTo("/messages");
            assertThat(call.authorized()).isFalse();
            assertThat(call.status()).isEqualTo(401);
        });
        assertThat(server.calls()).noneSatisfy(call -> {
            assertThat(call.rpcMethod()).isEqualTo("tools/call");
            assertThat(call.authorized()).isFalse();
            assertThat(call.status()).isEqualTo(202);
        });
    }

    private List<AuditIssue> auditToolCallAgainst(Guard guard) throws Exception {
        server = LegacySseMcpServer.start(guard);
        manager = new McpClientManager(McpEventLog.noop());
        manager.connect(new McpServerConfig(server.sseUrl(), TransportType.SSE, BEARER));
        McpScannerSession session = manager.scannerSession();
        proxy = TestSseProxies.builder(session).build();
        proxy.start();
        session.setSseProxyPort(proxy::port);
        Http http = routedThroughProxy(session);
        HttpRequestResponse base = http.sendRequest(baseRequest(session));
        assertThat(base.response().bodyToString()).contains(LegacySseMcpServer.SECRET);
        return new UnauthenticatedRequestExecutionCheck(McpEventLog.noop(), () -> BEARER, session::probeBaselineService,
                ScriptedEventStreams.none()).audit(base, http);
    }

    private HttpRequest baseRequest(McpScannerSession session) {
        Map<String, String> headers = new LinkedHashMap<>(session.scannerHeaders());
        headers.putAll(proxy.auditHeaders(session.scannerHeaders()));
        headers.put("Authorization", LegacySseMcpServer.TOKEN);
        headers.put("Content-Type", "application/json");
        HttpRequest request = HttpRequest.httpRequestFromUrl(session.resolvedEndpoint()).withMethod("POST");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request = request.withHeader(header.getKey(), header.getValue());
        }
        return request.withBody(toolCall());
    }

    private Http routedThroughProxy(McpScannerSession session) {
        McpHttpHandler handler = new McpHttpHandler(session, proxy, mock(Http1LoopbackRelay.class));
        VerbatimHeaderHttp wire = new VerbatimHeaderHttp();
        return new SendOnlyHttp() {
            @Override
            public HttpRequestResponse sendRequest(HttpRequest request) {
                return HttpRequestResponse.httpRequestResponse(request,
                        wire.sendRequest(handler.route(request)).response());
            }
        };
    }
}
