package com.mcpscanner.checks.content;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.sitemap.SiteMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.checks.content.rules.AwsAccessKeyRule;
import com.mcpscanner.checks.registry.ContentRuleDescriptor;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.proxy.observe.Direction;
import com.mcpscanner.proxy.observe.ExposureSurface;
import com.mcpscanner.proxy.observe.McpExchange;
import com.mcpscanner.proxy.observe.McpExchangeLog;
import com.mcpscanner.proxy.observe.ObservedMessage;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveContentPassiveRunnerTest {

    private static final String REAL_AWS_KEY = "AKIAQ7777PYTYINTERNAL";
    private static final String CANONICAL_PLACEHOLDER = "AKIAIOSFODNN7EXAMPLE";

    private MontoyaApi api;
    private SiteMap siteMap;
    private ScanCheckSettings settings;
    private ContentFindingDedup dedup;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        api = mock(MontoyaApi.class);
        siteMap = mock(SiteMap.class);
        when(api.siteMap()).thenReturn(siteMap);
        settings = mock(ScanCheckSettings.class);
        lenient().when(settings.isEnabled(any(String.class), any(Boolean.class))).thenReturn(true);
        dedup = new ContentFindingDedup();
    }

    // ---------------------------------------------------------------------------------------
    // True positive — a real secret leaked in MCP runtime output
    // ---------------------------------------------------------------------------------------

    @Test
    void emitsIssueForRealSecretInToolCallOutput() {
        runner().scan(toolCallExchange(textResult(REAL_AWS_KEY)));

        List<AuditIssue> issues = capturedIssues();
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).name())
                .isEqualTo(new AwsAccessKeyRule().displayName()
                        + ContentFindingIssueBuilder.RESPONSE_SOURCE.nameQualifier());
    }

    @Test
    void emitsIssueForSecretInResourceReadOutput() {
        JsonNode result = parse("{\"contents\":[{\"text\":\"deploy " + REAL_AWS_KEY + "\"}]}");

        runner().scan(exchange("resources/read", result));

        assertThat(capturedIssues()).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------
    // False-positive guardrails — must NOT emit
    // ---------------------------------------------------------------------------------------

    @Test
    void doesNotEmitForBenignToolOutput() {
        runner().scan(toolCallExchange(textResult("the deployment completed successfully")));

        verify(siteMap, never()).add(any(AuditIssue.class));
    }

    @Test
    void doesNotEmitForCanonicalPlaceholder() {
        runner().scan(toolCallExchange(textResult("use key " + CANONICAL_PLACEHOLDER)));

        verify(siteMap, never()).add(any(AuditIssue.class));
    }

    @Test
    void doesNotEmitWhenMasterToggleOff() {
        when(settings.isEnabled(ContentRuleDescriptor.MASTER_ID, true)).thenReturn(false);

        runner().scan(toolCallExchange(textResult(REAL_AWS_KEY)));

        verify(siteMap, never()).add(any(AuditIssue.class));
    }

    @Test
    void doesNotEmitForNonRuntimeMethod() {
        JsonNode result = parse("{\"tools\":[{\"name\":\"leak\",\"description\":\"" + REAL_AWS_KEY + "\"}]}");

        runner().scan(exchange("tools/list", result));

        verify(siteMap, never()).add(any(AuditIssue.class));
    }

    @Test
    void doesNotEmitForNullDecodedResponse() {
        runner().scan(exchange("tools/call", null));

        verify(siteMap, never()).add(any(AuditIssue.class));
    }

    @Test
    void doesNotEmitWhenHostUnavailable() {
        McpExchange noHost = new McpExchange("s", TransportType.STREAMABLE_HTTP, Direction.SERVER_TO_CLIENT,
                "1", 0, "tools/call", null, textResult(REAL_AWS_KEY), 200, Instant.now(),
                ExposureSurface.LIVE_RUNTIME_OUTPUT);

        runner().scan(noHost);

        verify(siteMap, never()).add(any(AuditIssue.class));
    }

    // ---------------------------------------------------------------------------------------
    // Dedup — runtime output is its own exposure surface
    // ---------------------------------------------------------------------------------------

    @Test
    void dedupesRepeatedSecretAcrossExchangesAtLiveRuntimeSurface() {
        LiveContentPassiveRunner runner = runner();

        runner.scan(toolCallExchange(textResult(REAL_AWS_KEY)));
        runner.scan(toolCallExchange(textResult(REAL_AWS_KEY)));

        assertThat(capturedIssues()).hasSize(1);
    }

    @Test
    void liveFindingIsIndependentOfADiscoveryClaimForTheSameSecret() {
        // A discovery-surface claim must not suppress the live-runtime finding: they are distinct
        // exposures with distinct remediations.
        dedup.tryClaim(new AwsAccessKeyRule().id(), REAL_AWS_KEY, "https://mcp.example.test:443",
                ExposureSurface.DISCOVERY_METADATA);

        runner().scan(toolCallExchange(textResult(REAL_AWS_KEY)));

        assertThat(capturedIssues()).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------
    // Seam-level end-to-end — the runner must fire on the PRODUCTION-shaped exchange the
    // McpExchangeLog actually produces (response row has null method + null request; the log
    // enriches it from the correlated request before scanning).
    // ---------------------------------------------------------------------------------------

    @Test
    void firesOnProductionShapedExchangeWiredThroughMcpExchangeLog() {
        HttpRequest request = requestWithHost();
        McpExchangeLog exchangeLog = new McpExchangeLog(runner());

        // (a) the client request carries the originating method + the host-bearing HttpRequest.
        exchangeLog.observe(productionRequest(request));
        // (b) the production-shaped response: null method, null request, parsed envelope with the secret.
        exchangeLog.observe(productionResponse(wrap(textResult(REAL_AWS_KEY))));

        assertThat(capturedIssues()).hasSize(1);
    }

    @Test
    void doesNotFireOnProductionShapedExchangeForBenignOutput() {
        HttpRequest request = requestWithHost();
        McpExchangeLog exchangeLog = new McpExchangeLog(runner());

        exchangeLog.observe(productionRequest(request));
        exchangeLog.observe(productionResponse(wrap(textResult("the deployment completed successfully"))));

        verify(siteMap, never()).add(any(AuditIssue.class));
    }

    private static ObservedMessage productionRequest(HttpRequest request) {
        return new ObservedMessage(Direction.CLIENT_TO_SERVER, TransportType.STREAMABLE_HTTP, "session", "1",
                "tools/call", request, parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"}"), null);
    }

    private static ObservedMessage productionResponse(JsonNode parsedEnvelope) {
        // Mirrors BurpObserverAdapter.observeResponse: a JSON-RPC response envelope has no method and
        // the response row carries no request — both are null. McpExchangeLog must enrich from the
        // correlated request before handing the row to the runner.
        return new ObservedMessage(Direction.SERVER_TO_CLIENT, TransportType.STREAMABLE_HTTP, "session", "1",
                null, null, parsedEnvelope, 200);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private LiveContentPassiveRunner runner() {
        return new LiveContentPassiveRunner(List.of(new AwsAccessKeyRule()), settings, api, dedup);
    }

    private List<AuditIssue> capturedIssues() {
        ArgumentCaptor<AuditIssue> captor = ArgumentCaptor.forClass(AuditIssue.class);
        verify(siteMap, atLeastOnce()).add(captor.capture());
        return captor.getAllValues();
    }

    private static JsonNode textResult(String text) {
        return parse("{\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]}");
    }

    private static McpExchange toolCallExchange(JsonNode result) {
        return exchange("tools/call", result);
    }

    private static McpExchange exchange(String method, JsonNode result) {
        return new McpExchange("session", TransportType.STREAMABLE_HTTP, Direction.SERVER_TO_CLIENT,
                "1", 0, method, requestWithHost(), wrap(result), 200, Instant.now(),
                ExposureSurface.LIVE_RUNTIME_OUTPUT);
    }

    private static HttpRequest requestWithHost() {
        HttpService service = mock(HttpService.class);
        lenient().when(service.host()).thenReturn("mcp.example.test");
        lenient().when(service.port()).thenReturn(443);
        lenient().when(service.secure()).thenReturn(true);
        HttpRequest request = mock(HttpRequest.class);
        lenient().when(request.httpService()).thenReturn(service);
        return request;
    }

    private static JsonNode wrap(JsonNode result) {
        if (result == null) {
            return null;
        }
        return parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + result + "}");
    }

    private static JsonNode parse(String json) {
        try {
            return McpObjectMapper.INSTANCE.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
