package com.mcpscanner.checks.registry;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.shared.scancontext.CurrentScan;
import com.mcpscanner.checks.shared.scancontext.ScanContext;
import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.LoopbackCollaborator;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SkipLoggingGuardTest {

    private static final String ENDPOINT = "http://127.0.0.1:9/mcp";
    private static final String TOKEN = "guard-token";
    private static final List<String> BODIES = List.of(
            "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\","
                    + "\"arguments\":{\"path\":\"notes.txt\",\"url\":\"https://example.com/a\"}}}",
            "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/list\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"resources/read\",\"params\":{\"uri\":\"file:///notes.txt\"}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

    private static final int MANY_FIELDS = 40;
    private static final int MAX_DISTINCT_SKIP_LINES = 6;

    private final List<String> violations = new ArrayList<>();
    private LoopbackCollaborator collaborator;

    @BeforeEach
    void setUp() throws IOException {
        MontoyaTestFactory.install();
        collaborator = LoopbackCollaborator.start();
    }

    @AfterEach
    void tearDown() {
        collaborator.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogCheckIds")
    void everyInvocationThatSendsNothingAndFindsNothingSaysWhy(String checkId) {
        for (HttpRequestResponse base : bases(true)) {
            forEachInvocation(checkId, base, FakeMcpServer::respond, this::requireReasonWhenIdle);
        }
        for (HttpRequestResponse base : bases(false)) {
            forEachInvocation(checkId, base, FakeMcpServer::respond, this::requireReasonWhenIdle);
        }

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogCheckIds")
    void everyInvocationThatMeetsHttp429SaysItWasRateLimited(String checkId) {
        for (HttpRequestResponse base : bases(true)) {
            forEachInvocation(checkId, base, SkipLoggingGuardTest::tooManyRequests, this::requireRateLimitLine);
        }

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogCheckIds")
    void aManyFieldScanLogsEachSkipReasonOncePerCheck(String checkId) {
        HttpRequestResponse base = new ScriptedProbeHttp(FakeMcpServer::respond).sendRequest(request(manyFieldCall(), true));
        McpEventLog eventLog = new McpEventLog(null);
        ScanContext scan = new ScanContext(1L);

        for (Function<ProtocolHttpRequest, ProtocolHttpResponse> server : List.of(
                (Function<ProtocolHttpRequest, ProtocolHttpResponse>) FakeMcpServer::respond,
                SkipLoggingGuardTest::tooManyRequests)) {
            for (Optional<AuditInsertionPoint> point : pointsFor(checkId, base)) {
                McpCheckAdapter<?> adapter = adapterFor(checkId, eventLog);
                CurrentScan.within(Optional.of(scan), () -> adapter.doCheck(base, point.orElse(null),
                        new ScriptedProbeHttp(server)));
            }
        }

        assertThat(eventLog.snapshot()).filteredOn(entry -> entry.level() == McpEventLog.Level.INFO)
                .extracting(McpEventLog.LogEntry::message)
                .filteredOn(line -> isSkipLine(line.substring(CheckLog.prefix(checkId).length())))
                .hasSizeLessThanOrEqualTo(MAX_DISTINCT_SKIP_LINES);
    }

    private static String manyFieldCall() {
        String arguments = IntStream.range(0, MANY_FIELDS)
                .mapToObj(index -> "\"arg" + index + "\":\"value" + index + "\"")
                .collect(Collectors.joining(","));
        return "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\","
                + "\"arguments\":{" + arguments + "}}}";
    }

    static Stream<String> catalogCheckIds() {
        return CheckCatalog.discover(CheckServices.standalone()).all().stream()
                .map(check -> check.descriptor().id())
                .filter(id -> !id.startsWith("fixture"));
    }

    private void forEachInvocation(String checkId, HttpRequestResponse base,
                                   Function<ProtocolHttpRequest, ProtocolHttpResponse> server,
                                   InvocationRule rule) {
        for (Optional<AuditInsertionPoint> point : pointsFor(checkId, base)) {
            McpEventLog eventLog = new McpEventLog(null);
            ScriptedProbeHttp http = new ScriptedProbeHttp(server);
            McpCheckAdapter<?> adapter = adapterFor(checkId, eventLog);
            int issues = adapter.doCheck(base, point.orElse(null), http).auditIssues().size();
            rule.check(new Invocation(checkId, base, point, http.sent(), issues, reasonLines(checkId, eventLog)))
                    .ifPresent(violations::add);
        }
    }

    private Optional<String> requireReasonWhenIdle(Invocation invocation) {
        boolean idle = invocation.requests() == 0 && invocation.issues() == 0;
        boolean explained = invocation.lines().stream().anyMatch(SkipLoggingGuardTest::isSkipLine);
        return idle && !explained
                ? Optional.of(invocation.describe("sent nothing, found nothing and logged no skip reason"))
                : Optional.empty();
    }

    private Optional<String> requireRateLimitLine(Invocation invocation) {
        boolean explained = invocation.lines().stream()
                .anyMatch(line -> isSkipLine(line) && line.contains("(" + SkipReason.Kind.RATE_LIMITED.label()));
        return invocation.requests() > 0 && invocation.issues() == 0 && !explained
                ? Optional.of(invocation.describe("met HTTP 429 and logged no rate-limit stop"))
                : Optional.empty();
    }

    private static boolean isSkipLine(String line) {
        return (line.startsWith("skipped ") || line.startsWith("stopped ")) && line.endsWith(")")
                && Arrays.stream(SkipReason.Kind.values()).anyMatch(kind -> line.contains(" (" + kind.label()));
    }

    private List<Optional<AuditInsertionPoint>> pointsFor(String checkId, HttpRequestResponse base) {
        if (catalog(McpEventLog.noop()).find(checkId).orElseThrow().descriptor().kind() != CheckKind.INSERTION_POINT) {
            return List.of(Optional.empty());
        }
        return new McpInsertionPointProvider().provideInsertionPoints(base).stream()
                .map(Optional::of)
                .toList();
    }

    private McpCheckAdapter<?> adapterFor(String checkId, McpEventLog eventLog) {
        McpCheck check = catalog(eventLog).find(checkId).orElseThrow();
        return new McpCheckAdapters(AdapterFixtures.settingsAllowing(true), eventLog).adapt(check);
    }

    private CheckCatalog catalog(McpEventLog eventLog) {
        return CheckCatalog.discover(new CheckServices(eventLog, () -> new BearerTokenAuthStrategy(TOKEN),
                () -> Optional.of(collaborator), Optional::empty, PrincipalRoster::empty, AccessLedger.noop(),
                PrincipalListings.unknown()));
    }

    private static List<String> reasonLines(String checkId, McpEventLog eventLog) {
        String prefix = CheckLog.prefix(checkId);
        return eventLog.snapshot().stream()
                .map(McpEventLog.LogEntry::message)
                .filter(message -> message.startsWith(prefix))
                .map(message -> message.substring(prefix.length()))
                .toList();
    }

    private static List<HttpRequestResponse> bases(boolean inSession) {
        Http http = new ScriptedProbeHttp(FakeMcpServer::respond);
        return BODIES.stream().map(body -> http.sendRequest(request(body, inSession))).toList();
    }

    private static HttpRequest request(String body, boolean inSession) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(ENDPOINT)
                .withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream")
                .withHeader("Authorization", "Bearer " + TOKEN)
                .withBody(body);
        return inSession ? request.withHeader("Mcp-Session-Id", FakeMcpServer.SESSION_ID) : request;
    }

    private static ProtocolHttpResponse tooManyRequests(ProtocolHttpRequest request) {
        return FakeMcpServer.exchange(request, 429, Map.of("Content-Type", List.of("text/plain")), "slow down");
    }

    @FunctionalInterface
    private interface InvocationRule {
        Optional<String> check(Invocation invocation);
    }

    private record Invocation(String checkId, HttpRequestResponse base, Optional<AuditInsertionPoint> point,
                              int requests, int issues, List<String> lines) {

        String describe(String problem) {
            return checkId + " on " + base.request().bodyToString()
                    + point.map(p -> " field " + p.name()).orElse("") + ": " + problem
                    + " (" + requests + " requests, log " + lines + ")";
        }
    }
}
