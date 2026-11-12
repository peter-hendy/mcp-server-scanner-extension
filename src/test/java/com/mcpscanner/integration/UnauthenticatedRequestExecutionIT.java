package com.mcpscanner.integration;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.cvetargets.requests.SendOnlyHttp;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.proxy.Http1LoopbackRelay;
import com.mcpscanner.proxy.McpHttpHandler;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.proxy.TestSseProxies;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class UnauthenticatedRequestExecutionIT {

    private static final String CHECK_ID = "unauthenticated-request-execution";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOOL_CALL =
            "{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\","
                    + "\"arguments\":{\"message\":\"scanner-echo-marker\"}}}";
    private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":22,\"method\":\"tools/list\",\"params\":{}}";
    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":23,\"method\":\"ping\"}";
    private static final String NOTIFICATION = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    private static final List<String> REPEATED_SEEDS = List.of(
            echoCall(31, "second"), echoCall(32, "third"), echoCall(33, "fourth"), echoCall(34, "fifth"));

    private final CountingHttp http = new CountingHttp(new VerbatimHeaderHttp());

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void anonymousExecutionFiresInVulnerableModeAndIsSilentInSafeMode() throws Exception {
        Map<String, List<AuditIssue>> vulnerable = scan("unauthenticated_execution=vulnerable");

        assertThat(vulnerable.get(TOOL_CALL)).singleElement().satisfies(issue ->
                assertRated(issue, "MCP request executed without authentication", AuditIssueSeverity.MEDIUM,
                        AuditIssueConfidence.FIRM));
        assertThat(vulnerable.get(TOOLS_LIST)).singleElement().satisfies(issue ->
                assertRated(issue, "MCP catalog listed without authentication", AuditIssueSeverity.INFORMATION,
                        AuditIssueConfidence.FIRM));
        assertThat(vulnerable.get(PING)).isEmpty();
        assertThat(vulnerable.get(NOTIFICATION)).isEmpty();

        assertThat(scan("unauthenticated_execution=safe").values()).allSatisfy(issues -> assertThat(issues).isEmpty());
    }

    @Test
    void forwardedLoopbackBypassFiresOnEveryMethodClassAndIsSilentInSafeMode() throws Exception {
        Map<String, List<AuditIssue>> vulnerable = scan("auth_forwarded_loopback_trust=vulnerable");

        assertThat(vulnerable.get(TOOL_CALL)).singleElement().satisfies(issue -> {
            assertRated(issue, "MCP authentication bypass", AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM);
            assertThat(issue.detail()).containsAnyOf("forwarded-for", "client.x-real-ip");
        });
        assertThat(vulnerable.get(TOOLS_LIST)).singleElement().satisfies(issue ->
                assertRated(issue, "MCP authentication bypass", AuditIssueSeverity.LOW, AuditIssueConfidence.FIRM));
        assertThat(vulnerable.get(PING)).singleElement().satisfies(issue ->
                assertRated(issue, "MCP authentication bypass", AuditIssueSeverity.LOW, AuditIssueConfidence.TENTATIVE));
        assertThat(vulnerable.get(NOTIFICATION)).singleElement().satisfies(issue ->
                assertRated(issue, "MCP authentication bypass", AuditIssueSeverity.INFORMATION,
                        AuditIssueConfidence.TENTATIVE));

        assertThat(scan("auth_forwarded_loopback_trust=safe").values())
                .allSatisfy(issues -> assertThat(issues).isEmpty());
    }

    @Test
    void sessionCredentialFiresInVulnerableModeAndIsSilentInSafeMode() throws Exception {
        Map<String, List<AuditIssue>> vulnerable = scan("auth_session_credential=vulnerable");

        assertThat(vulnerable.get(TOOL_CALL)).singleElement().satisfies(issue ->
                assertRated(issue, "MCP session id accepted as credential", AuditIssueSeverity.LOW,
                        AuditIssueConfidence.FIRM));
        assertThat(vulnerable.get(TOOLS_LIST)).singleElement().satisfies(issue ->
                assertThat(issue.name()).isEqualTo("MCP session id accepted as credential"));

        assertThat(scan("auth_session_credential=safe").values()).allSatisfy(issues -> assertThat(issues).isEmpty());
    }

    @Test
    void sessionCredentialFiresThroughTheScannerProxyAndIsSilentInSafeMode() throws Exception {
        assertThat(scanThroughScannerProxy("auth_session_credential=vulnerable")).anySatisfy(issue ->
                assertRated(issue, "MCP session id accepted as credential", AuditIssueSeverity.LOW,
                        AuditIssueConfidence.FIRM));
        assertThat(scanThroughScannerProxy("auth_session_credential=safe")).isEmpty();
    }

    @Test
    void presenceOnlyCredentialFiresTheInvalidCredentialTierAndIsSilentInSafeMode() throws Exception {
        Map<String, List<AuditIssue>> vulnerable = scan("auth_presence_only_credential=vulnerable");

        assertThat(vulnerable.get(TOOL_CALL)).singleElement().satisfies(issue ->
                assertRated(issue, "MCP invalid credential accepted", AuditIssueSeverity.MEDIUM,
                        AuditIssueConfidence.FIRM));
        assertThat(vulnerable.get(TOOLS_LIST)).singleElement().satisfies(issue ->
                assertRated(issue, "MCP invalid credential accepted", AuditIssueSeverity.INFORMATION,
                        AuditIssueConfidence.FIRM));
        assertThat(vulnerable.get(PING)).isEmpty();
        assertThat(vulnerable.get(NOTIFICATION)).isEmpty();

        assertThat(scan("auth_presence_only_credential=safe").values())
                .allSatisfy(issues -> assertThat(issues).isEmpty());
    }

    @Test
    void repeatedSeedsOfAMethodAreNotSweptAgainAndStillReportTheirBypass() throws Exception {
        SweepCost safe = sweepCost("auth_forwarded_loopback_trust=safe");
        SweepCost vulnerable = sweepCost("auth_forwarded_loopback_trust=vulnerable");
        System.out.printf("unauthenticated-request-execution test-server requests: safe first=%d repeated=%d, "
                + "vulnerable first=%d repeated=%d%n", safe.firstSeedRequests(), safe.repeatedSeedRequests(),
                vulnerable.firstSeedRequests(), vulnerable.repeatedSeedRequests());

        assertThat(safe.issueNames()).isEmpty();
        assertThat(safe.repeatedSeedRequests() / REPEATED_SEEDS.size()).isLessThan(safe.firstSeedRequests() / 8);
        assertThat(vulnerable.issueNames()).hasSize(REPEATED_SEEDS.size() + 1)
                .containsOnly("MCP authentication bypass");
    }

    private static String echoCall(int id, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{\"name\":\"echo\","
                + "\"arguments\":{\"message\":\"" + message + "\"}}}";
    }

    private static final class CountingHttp extends SendOnlyHttp {

        private final Http delegate;
        private final AtomicInteger sent = new AtomicInteger();

        CountingHttp(Http delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpRequestResponse sendRequest(HttpRequest request) {
            sent.incrementAndGet();
            return delegate.sendRequest(request);
        }

        int sent() {
            return sent.get();
        }
    }

    private record SweepCost(int firstSeedRequests, int repeatedSeedRequests, List<String> issueNames) {
    }

    private SweepCost sweepCost(String vuln) throws Exception {
        try (McpCheckItSupport.RunningServer server =
                     McpCheckItSupport.startServer("--default-mode", "safe", "--vuln", vuln)) {
            String endpoint = McpCheckItSupport.mcpEndpoint(server.port());
            String token = mintToken(server.port());
            Map<String, String> headers = Map.of("Authorization", "Bearer " + token,
                    "Mcp-Session-Id", openSession(endpoint, token));
            McpCheck check = CheckCatalog.discover(new CheckServices(McpEventLog.noop(),
                    () -> new BearerTokenAuthStrategy(token), Optional::empty, Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                    .find(CHECK_ID).orElseThrow();
            CheckRunner runner = new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints);
            List<HttpRequestResponse> repeated = REPEATED_SEEDS.stream()
                    .map(body -> base(endpoint, headers, body)).toList();
            HttpRequestResponse first = base(endpoint, headers, TOOL_CALL);
            int beforeFirst = http.sent();
            List<AuditIssue> issues = new ArrayList<>(runner.run(check, List.of(first)));
            int beforeRepeated = http.sent();
            issues.addAll(runner.run(check, repeated));
            return new SweepCost(beforeRepeated - beforeFirst, http.sent() - beforeRepeated,
                    issues.stream().map(AuditIssue::name).toList());
        }
    }

    private Map<String, List<AuditIssue>> scan(String vuln) throws Exception {
        try (McpCheckItSupport.RunningServer server =
                     McpCheckItSupport.startServer("--default-mode", "safe", "--vuln", vuln)) {
            String endpoint = McpCheckItSupport.mcpEndpoint(server.port());
            String token = mintToken(server.port());
            Map<String, String> headers = Map.of("Authorization", "Bearer " + token,
                    "Mcp-Session-Id", openSession(endpoint, token));
            McpCheck check = CheckCatalog.discover(new CheckServices(McpEventLog.noop(),
                    () -> new BearerTokenAuthStrategy(token), Optional::empty, Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                    .find(CHECK_ID).orElseThrow();
            CheckRunner runner = new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints);
            return Map.of(
                    TOOL_CALL, runner.run(check, List.of(base(endpoint, headers, TOOL_CALL))),
                    TOOLS_LIST, runner.run(check, List.of(base(endpoint, headers, TOOLS_LIST))),
                    PING, runner.run(check, List.of(base(endpoint, headers, PING))),
                    NOTIFICATION, runner.run(check, List.of(base(endpoint, headers, NOTIFICATION))));
        }
    }

    private List<AuditIssue> scanThroughScannerProxy(String vuln) throws Exception {
        try (McpCheckItSupport.RunningServer server =
                     McpCheckItSupport.startServer("--default-mode", "safe", "--vuln", vuln)) {
            String endpoint = McpCheckItSupport.mcpEndpoint(server.port());
            String token = mintToken(server.port());
            try (ScannerProxy proxy = ScannerProxy.connect(endpoint, new BearerTokenAuthStrategy(token))) {
                McpCheck check = CheckCatalog.discover(new CheckServices(McpEventLog.noop(),
                        () -> new BearerTokenAuthStrategy(token), Optional::empty, Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                        .find(CHECK_ID).orElseThrow();
                Http routed = proxy.routed(http);
                return new CheckRunner(routed, new McpInsertionPointProvider()::provideInsertionPoints)
                        .run(check, List.of(base(routed, endpoint, proxy.baseHeaders(), TOOL_CALL)));
            }
        }
    }

    private record ScannerProxy(McpScannerSession session, SseProxyServer proxy, McpHttpHandler handler)
            implements AutoCloseable {

        static ScannerProxy connect(String endpoint, BearerTokenAuthStrategy auth) throws Exception {
            McpScannerSession session = new McpScannerSession(McpCheckItSupport.realHttpClient(), McpEventLog.noop());
            session.connect(new McpServerConfig(endpoint, TransportType.STREAMABLE_HTTP, auth));
            SseProxyServer proxy = TestSseProxies.builder(session).build();
            proxy.start();
            session.setSseProxyPort(proxy::port);
            return new ScannerProxy(session, proxy, new McpHttpHandler(session, proxy, mock(Http1LoopbackRelay.class)));
        }

        Map<String, String> baseHeaders() {
            Map<String, String> headers = new java.util.LinkedHashMap<>(session.scannerHeaders());
            headers.putAll(proxy.auditHeaders(session.scannerHeaders()));
            return headers;
        }

        Http routed(Http delegate) {
            return new SendOnlyHttp() {
                @Override
                public HttpRequestResponse sendRequest(HttpRequest request) {
                    return HttpRequestResponse.httpRequestResponse(request,
                            delegate.sendRequest(handler.route(request)).response());
                }
            };
        }

        @Override
        public void close() {
            session.setSseProxyPort(null);
            proxy.stop();
            session.disconnect();
        }
    }

    private HttpRequestResponse base(String endpoint, Map<String, String> headers, String body) {
        return base(http, endpoint, headers, body);
    }

    private static HttpRequestResponse base(Http http, String endpoint, Map<String, String> headers, String body) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request = request.withHeader(header.getKey(), header.getValue());
        }
        HttpRequestResponse base = http.sendRequest(request.withBody(body));
        assertThat(base.response().statusCode()).as("authenticated base request").isBetween((short) 200, (short) 299);
        return base;
    }

    private static String mintToken(int port) throws Exception {
        java.net.http.HttpResponse<String> response = client().send(java.net.http.HttpRequest.newBuilder(
                        URI.create("http://" + McpCheckItSupport.HOST + ":" + port + "/_test/mint-token"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json").build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(response.body()).path("access_token").asText();
    }

    private static String openSession(String endpoint, String token) throws Exception {
        java.net.http.HttpResponse<String> initialize = post(endpoint, token, null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\","
                        + "\"capabilities\":{},\"clientInfo\":{\"name\":\"it\",\"version\":\"1\"}}}");
        String sessionId = initialize.headers().firstValue("mcp-session-id").orElseThrow();
        post(endpoint, token, sessionId, NOTIFICATION);
        return sessionId;
    }

    private static java.net.http.HttpResponse<String> post(String endpoint, String token, String sessionId,
                                                           String body) throws Exception {
        java.net.http.HttpRequest.Builder request = java.net.http.HttpRequest.newBuilder(URI.create(endpoint))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + token);
        if (sessionId != null) {
            request.header("Mcp-Session-Id", sessionId);
        }
        return client().send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private static void assertRated(AuditIssue issue, String name, AuditIssueSeverity severity,
                                    AuditIssueConfidence confidence) {
        assertThat(issue.name()).isEqualTo(name);
        assertThat(issue.severity()).isEqualTo(severity);
        assertThat(issue.confidence()).isEqualTo(confidence);
    }
}
