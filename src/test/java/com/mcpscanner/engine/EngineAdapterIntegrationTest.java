package com.mcpscanner.engine;

import burp.StreamingRefusalStandIn;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.api.RecordingChecks;
import com.mcpscanner.checks.api.RequestCheck;
import com.mcpscanner.checks.registry.CheckInstrumentation;
import com.mcpscanner.checks.registry.ScanCheckRegistry;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.mcpscanner.http.ConnectionReuse;
import com.mcpscanner.http.UpstreamExchanges;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RawHttpRequestStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.mcpscanner.engine.EngineFixtures.LABEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineAdapterIntegrationTest {

    private static final String TOOLS_CALL = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"hi\"}}}";
    private static final String NOTIFICATION = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
            + "\"params\":{\"requestId\":\"x\"}}";

    private final McpInsertionPointProvider insertionPoints = new McpInsertionPointProvider();
    private ScanCheckSettings settings;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        settings = new ScanCheckSettings(InMemoryPersistedObject.configStore());
    }

    @Test
    void aDisabledCheckIsNeverAudited() {
        RecordingChecks.Request enabled = new RecordingChecks.Request("enabled", List.of());
        RecordingChecks.Request disabled = new RecordingChecks.Request("disabled", List.of());
        settings.setEnabled("disabled", false);

        scan(List.of(base(TOOLS_CALL, "a.test")), enabled, disabled);

        assertThat(enabled.calls).hasSize(1);
        assertThat(disabled.calls).isEmpty();
    }

    @Test
    void aHostCheckRunsOncePerHost() {
        RecordingChecks.Host host = new RecordingChecks.Host("host", List.of());
        HttpRequestResponse first = base(TOOLS_CALL, "a.test");
        HttpRequestResponse other = base(TOOLS_CALL, "b.test");

        scan(List.of(first, base(TOOLS_CALL, "a.test"), other), host);

        assertThat(host.calls).extracting(RecordingChecks.Call::base).containsExactly(first, other);
    }

    @Test
    void aReplyReadingRequestCheckSkipsNotifications() {
        RecordingChecks.Request request = new RecordingChecks.Request("request", List.of()).requiringReply();
        HttpRequestResponse call = base(TOOLS_CALL, "a.test");

        scan(List.of(base(NOTIFICATION, "a.test"), call), request);

        assertThat(request.calls).extracting(RecordingChecks.Call::base).containsExactly(call);
    }

    @Test
    void anInsertionPointCheckRunsOncePerProvidedInsertionPoint() {
        RecordingChecks.InsertionPoint field = new RecordingChecks.InsertionPoint("field", List.of());
        HttpRequestResponse call = base(TOOLS_CALL, "a.test");
        List<String> provided = namesOf(insertionPoints.provideInsertionPoints(call));

        scan(List.of(call), field);

        assertThat(provided).contains("MCP field: params.name", "MCP field: params.arguments.text");
        assertThat(field.calls).extracting(invocation -> invocation.insertionPoint().name())
                .containsExactlyInAnyOrderElementsOf(provided);
    }

    @Test
    void issuesAddedToTheSiteMapAfterAStreamingRefusalCountAsReported() {
        AuditIssue issue = AuditIssue.auditIssue("Streamed", "Detail", "Fix", "https://a.test/mcp",
                AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM, "Background", "Fix background",
                AuditIssueSeverity.HIGH, List.of());
        FakeSiteMap siteMap = new FakeSiteMap();

        ScanProgress progress = scan(refusingStreamedReplies(), siteMap, List.of(base(TOOLS_CALL, "a.test")),
                new RefusalRecoveringCheck(issue));

        assertThat(siteMap.added()).containsExactly(issue);
        assertThat(progress.issuesReported()).isEqualTo(1);
    }

    private void scan(List<HttpRequestResponse> bases, McpCheck... checks) {
        scan(mock(Http.class), new FakeSiteMap(), bases, checks);
    }

    private ScanProgress scan(Http http, FakeSiteMap siteMap, List<HttpRequestResponse> bases, McpCheck... checks) {
        McpScanEngine engine = new McpScanEngine(http, insertionPoints, siteMap,
                new FixedWorkers(new SameThreadExecutorService()), EngineFixtures.environment(McpEventLog.noop()));
        new ScanCheckRegistry(catalogOf(checks), settings, McpEventLog.noop(), CheckInstrumentation.NONE,
                UpstreamExchanges.NONE, ConnectionReuse.withoutLocalAnswers(), EngineFixtures.siteMapOf(engine))
                .registerWith(engine::register);
        return engine.scan(bases, LABEL).completion().join();
    }

    private static Http refusingStreamedReplies() {
        return mock(Http.class, invocation -> {
            throw new RuntimeException(new StreamingRefusalStandIn());
        });
    }

    private static CheckCatalog catalogOf(McpCheck... checks) {
        return CheckCatalog.of(Arrays.stream(checks)
                .map(check -> (CheckFactory) services -> check)
                .toList(), CheckServices.standalone());
    }

    private static List<String> namesOf(List<AuditInsertionPoint> points) {
        return points.stream().map(AuditInsertionPoint::name).toList();
    }

    private static HttpRequestResponse base(String body, String host) {
        HttpRequest request = RawHttpRequestStub.post(body, Map.of("Content-Type", "application/json"));
        HttpService service = HttpService.httpService(host, 443, true);
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        when(base.request()).thenReturn(request);
        when(base.hasResponse()).thenReturn(true);
        when(base.httpService()).thenReturn(service);
        return base;
    }

    private static final class RefusalRecoveringCheck implements RequestCheck {

        private final AuditIssue issue;

        RefusalRecoveringCheck(AuditIssue issue) {
            this.issue = issue;
        }

        @Override
        public CheckDescriptor descriptor() {
            return RecordingChecks.descriptorFor("refusal-recovering", CheckKind.REQUEST);
        }

        @Override
        public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
            try {
                http.sendRequest(base.request());
            } catch (RuntimeException recoveredOutsideBurp) {
                return List.of(issue);
            }
            return List.of();
        }
    }
}
