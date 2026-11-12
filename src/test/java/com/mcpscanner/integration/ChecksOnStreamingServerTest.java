package com.mcpscanner.integration;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.testutil.ClientCertificateBurpHttp;
import com.mcpscanner.testutil.HandlerRoutedBurpHttp;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.StreamingCampaignServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ChecksOnStreamingServerTest {

    private final McpEventLog eventLog = new McpEventLog(null);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @ParameterizedTest
    @ValueSource(strings = {"oauth-authorization-consent", "oauth-consent-structure-reflection",
            "oauth-redirect-policy", "origin-host-admission"})
    void checkRunsWithoutThrowingWhenBurpWithholdsTheServersStreamedReplies(String checkId) throws Exception {
        try (StreamingCampaignServer server = StreamingCampaignServer.vulnerable();
             ScannerProxyFixture proxy = ScannerProxyFixture.connect(server.endpoint(), TransportType.STREAMABLE_HTTP)) {
            Http burp = new HandlerRoutedBurpHttp(proxy.handler()::route,
                    ClientCertificateBurpHttp.withoutClientCertificate());
            HttpRequestResponse base = base(burp, proxy);

            assertThatCode(() -> new CheckRunner(burp, ignored -> List.of()).run(check(checkId), List.of(base)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void originHostAdmissionJudgesOriginAndHostOutsideBurpOnAServerThatStreams() throws Exception {
        try (StreamingCampaignServer server = StreamingCampaignServer.vulnerable();
             ScannerProxyFixture proxy = ScannerProxyFixture.connect(server.endpoint(), TransportType.STREAMABLE_HTTP)) {
            Http burp = streamRefusingBurp(proxy);
            HttpRequestResponse base = base(burp, proxy);

            List<AuditIssue> issues = new CheckRunner(burp, ignored -> List.of())
                    .run(check("origin-host-admission"), List.of(base, base));

            assertThat(issues).extracting(AuditIssue::name).containsExactly("MCP Origin header admission",
                    "MCP Host header admission", "MCP Host and Origin header admission");
            assertThat(issues).allSatisfy(issue -> assertThat(issue.detail())
                    .contains("with its own HTTP client, outside Burp"));
            assertThat(messages()).filteredOn(message -> message.contains(" via direct")).singleElement().asString()
                    .contains("Host: attacker.example -> ADMITTED");
        }
    }

    @Test
    void sessionLifecycleOpensAndClosesItsProbeSessionsOnAServerThatStreams() throws Exception {
        try (StreamingCampaignServer server = StreamingCampaignServer.sessionKeeping();
             ScannerProxyFixture proxy = ScannerProxyFixture.connect(server.endpoint(), TransportType.STREAMABLE_HTTP)) {
            Http burp = streamRefusingBurp(proxy);
            HttpRequestResponse base = base(burp, proxy);

            new CheckRunner(burp, ignored -> List.of()).run(check("session-lifecycle"), List.of(base));

            assertThat(server.issuedSessions()).hasSizeGreaterThan(2);
            assertThat(server.deletedSessions()).isNotEmpty()
                    .doesNotContain(proxy.baseHeaders().get(McpHeaders.SESSION_ID));
            assertThat(messages()).noneMatch(message -> message.contains(ClientCertificateBurpHttp.STREAMING_REFUSAL));
        }
    }

    @Test
    void crossClientResponseLeakRacesItsProbeSessionsOnAServerThatStreams() throws Exception {
        try (StreamingCampaignServer server = StreamingCampaignServer.sessionKeeping();
             ScannerProxyFixture proxy = ScannerProxyFixture.connect(server.endpoint(), TransportType.STREAMABLE_HTTP)) {
            Http burp = streamRefusingBurp(proxy);
            HttpRequestResponse base = base(burp, proxy, toolCallBody());

            new CheckRunner(burp, ignored -> List.of()).run(check("cross-client-response-leak"), List.of(base));

            assertThat(server.issuedSessions()).hasSizeGreaterThan(2);
            assertThat(messages()).noneMatch(message -> message.contains(ClientCertificateBurpHttp.STREAMING_REFUSAL)
                    || message.contains("probe session refused") || message.contains("probe call unanswered"));
        }
    }

    private List<String> messages() {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message).toList();
    }

    private static Http streamRefusingBurp(ScannerProxyFixture proxy) throws Exception {
        return new HandlerRoutedBurpHttp(proxy.handler()::route, ClientCertificateBurpHttp.withoutClientCertificate());
    }

    private static String toolCallBody() {
        return "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\""
                + StreamingCampaignServer.TOOL + "\",\"arguments\":{\"id\":\""
                + StreamingCampaignServer.ALICE_CAMPAIGN + "\"}}}";
    }

    private McpCheck check(String checkId) {
        return CheckCatalog.discover(new CheckServices(eventLog,
                () -> new BearerTokenAuthStrategy(StreamingCampaignServer.ALICE_TOKEN), Optional::empty,
                Optional::empty, PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                .find(checkId).orElseThrow();
    }

    private static HttpRequestResponse base(Http burp, ScannerProxyFixture proxy) {
        return base(burp, proxy, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/list\"}");
    }

    private static HttpRequestResponse base(Http burp, ScannerProxyFixture proxy, String body) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(proxy.session().resolvedEndpoint()).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream");
        for (Map.Entry<String, String> header : proxy.baseHeaders().entrySet()) {
            request = request.withHeader(header.getKey(), header.getValue());
        }
        HttpRequestResponse base = burp.sendRequest(request.withBody(body));
        assertThat(base.response().statusCode()).as("base request through the proxy").isEqualTo((short) 200);
        return base;
    }
}
