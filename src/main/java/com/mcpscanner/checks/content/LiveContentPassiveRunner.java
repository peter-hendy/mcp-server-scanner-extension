package com.mcpscanner.checks.content;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.checks.registry.ContentRuleDescriptor;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.mcp.McpRequestDetector.ResponseContentKind;
import com.mcpscanner.proxy.observe.ExposureSurface;
import com.mcpscanner.proxy.observe.McpExchange;
import com.mcpscanner.proxy.observe.PassiveLiveRunner;

import java.util.List;
import java.util.Objects;

/**
 * Live-traffic passive content scanner. Runs the same {@link ContentRuleEngine} the passive
 * {@code JsonRpcResponseContentScanner} uses, but over a correlated live {@link McpExchange}'s
 * decoded response rather than a Burp-scanned {@code HttpRequestResponse}, so secrets that surface
 * in proxied (non-scanned) MCP runtime output are still flagged.
 *
 * <p>Mirrors {@link DiscoveryContentScanner}'s emission: findings become host-only
 * {@link AuditIssue}s pushed to {@code siteMap().add(...)} with no HTTP evidence record (the live
 * exchange's raw request/response is not retained as a Burp message). Findings are deduped at the
 * {@link ExposureSurface#LIVE_RUNTIME_OUTPUT} surface — a secret already reported in discovery
 * metadata is a distinct exposure with a distinct remediation, so it is reported again here.
 *
 * <p>Gated by the content master toggle ({@link ContentRuleDescriptor#MASTER_ID}) like the other
 * content scanners. FP-safe: a non-runtime-output method, a missing/non-success result envelope, or
 * an exchange with no resolvable host produces no issue and never throws.
 */
public final class LiveContentPassiveRunner implements PassiveLiveRunner {

    private final ContentRuleEngine engine;
    private final ScanCheckSettings settings;
    private final MontoyaApi api;
    private final ContentFindingDedup dedup;

    public LiveContentPassiveRunner(List<ContentRule> rules, ScanCheckSettings settings, MontoyaApi api) {
        this(rules, settings, api, new ContentFindingDedup());
    }

    public LiveContentPassiveRunner(List<ContentRule> rules,
                                    ScanCheckSettings settings,
                                    MontoyaApi api,
                                    ContentFindingDedup dedup) {
        this.engine = new ContentRuleEngine(Objects.requireNonNull(rules, "rules must not be null"));
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.dedup = Objects.requireNonNull(dedup, "dedup must not be null");
    }

    @Override
    public void scan(McpExchange exchange) {
        if (exchange == null || !settings.isEnabled(ContentRuleDescriptor.MASTER_ID, true)) {
            return;
        }
        ResponseContentKind kind = McpRequestDetector.responseContentKindForMethod(exchange.method());
        if (kind == ResponseContentKind.OTHER) {
            return;
        }
        JsonNode result = successResult(exchange.decodedResponse());
        HttpService host = hostOf(exchange);
        if (result == null || host == null) {
            return;
        }
        List<InspectedField> fields = ResponseBodyContentExtractor.extractFromResult(kind, result, "");
        List<ContentFinding> findings = dedup.claimUnseen(
                engine.run(List.of(ContentRuleContext.forResponseBody(
                        McpRequestDetector.baseUrl(host), fields, host))),
                host,
                ExposureSurface.LIVE_RUNTIME_OUTPUT);
        for (AuditIssue issue : ContentFindingIssueBuilder.buildAll(
                findings, host, null, ContentFindingIssueBuilder.RESPONSE_SOURCE)) {
            api.siteMap().add(issue);
        }
    }

    private static JsonNode successResult(JsonNode decodedResponse) {
        if (decodedResponse == null || decodedResponse.has("error")) {
            return null;
        }
        JsonNode result = decodedResponse.path("result");
        return result.isObject() ? result : null;
    }

    private static HttpService hostOf(McpExchange exchange) {
        HttpRequest request = exchange.request();
        return request == null ? null : request.httpService();
    }
}
