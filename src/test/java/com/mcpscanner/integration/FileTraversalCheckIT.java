package com.mcpscanner.integration;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.requests.BaseRequestSet;
import com.mcpscanner.cvetargets.requests.BaseRequestScope;
import com.mcpscanner.cvetargets.requests.DiscoveredInventoryRequestSource;
import com.mcpscanner.cvetargets.requests.GeneratedRequestPlanner;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.scan.field.TemplateExpansionRegistry;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class FileTraversalCheckIT {

    private static final String DOCUMENT_HEADER = "X-Document-Path";

    enum Variant {
        PLAIN("traversal_plain", "plain", "file", "MCP arbitrary host file read"),
        ENCODED("traversal_encoded", "encoded", "encoded-file", "MCP file path traversal (filter bypass)"),
        PREFIX("traversal_prefix_sibling", "prefix", "prefix-file", "MCP file root boundary bypass (prefix sibling)");

        private final String vulnerability;
        private final String suffix;
        private final String scheme;
        private final String expectedIssue;

        Variant(String vulnerability, String suffix, String scheme, String expectedIssue) {
            this.vulnerability = vulnerability;
            this.suffix = suffix;
            this.scheme = scheme;
            this.expectedIssue = expectedIssue;
        }

        List<String> operations() {
            return List.of("tools/call read_file_" + suffix, "tools/call read_document_" + suffix,
                    "prompts/get review_file_" + suffix, "resources/read " + scheme + "://");
        }

        boolean owns(HttpRequest request) {
            String body = request.bodyToString();
            return body.contains("_" + suffix + "\"") || body.contains("\"" + scheme + "://");
        }
    }

    @BeforeAll
    static void installMontoya() {
        MontoyaTestFactory.install();
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void firesOnEverySurfaceOfAVulnerableVariant(Variant variant) throws Exception {
        List<AuditIssue> issues = scan(variant, "vulnerable");

        for (String operation : variant.operations()) {
            assertThat(issues).as(operation)
                    .anySatisfy(issue -> {
                        assertThat(issue.name()).isEqualTo(variant.expectedIssue);
                        assertThat(issue.detail()).contains("Operation: " + operation);
                    });
        }
    }

    @ParameterizedTest
    @EnumSource(Variant.class)
    void staysSilentOnASafeVariant(Variant variant) throws Exception {
        assertThat(scan(variant, "safe")).extracting(AuditIssue::name).isEmpty();
    }

    private static List<AuditIssue> scan(Variant variant, String mode) throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer("--default-mode", "safe",
                "--vuln", "unauthenticated_execution=vulnerable", "--vuln", variant.vulnerability + "=" + mode)) {
            TemplateExpansionRegistry templates = new TemplateExpansionRegistry();
            McpInsertionPointProvider provider = new McpInsertionPointProvider(() -> true, templates, BurpNativeJsonRpcFields.ABSENT);
            ArmEndpoint endpoint = new ArmEndpoint(URI.create(McpCheckItSupport.mcpEndpoint(server.port())),
                    new NoAuthStrategy());
            VerbatimHeaderHttp http = new VerbatimHeaderHttp();
            try (BaseRequestSet requests = new DiscoveredInventoryRequestSource(new GeneratedRequestPlanner(templates))
                    .open(endpoint, BaseRequestScope.unlimited(List.of()), http)) {
                List<HttpRequestResponse> bases = variantBases(requests.requests(), variant);
                assertThat(bases).extracting(base -> base.request().bodyToString())
                        .anyMatch(body -> body.contains("read_document_" + variant.suffix));
                McpCheck check = CheckCatalog.discover(CheckServices.standalone()).find("file-traversal").orElseThrow();
                return new CheckRunner(requests.route(http), base -> fields(provider, base)).run(check, bases);
            }
        }
    }

    private static List<HttpRequestResponse> variantBases(List<HttpRequestResponse> generated, Variant variant) {
        List<HttpRequestResponse> bases = new ArrayList<>();
        for (HttpRequestResponse exchange : generated) {
            if (!variant.owns(exchange.request())) {
                continue;
            }
            HttpRequest request = exchange.request().bodyToString().contains("read_document_")
                    ? exchange.request().withAddedHeader(DOCUMENT_HEADER, "readme.txt")
                    : exchange.request();
            bases.add(HttpRequestResponse.httpRequestResponse(request, exchange.response()));
        }
        return bases;
    }

    private static List<AuditInsertionPoint> fields(McpInsertionPointProvider provider, HttpRequestResponse base) {
        List<AuditInsertionPoint> points = new ArrayList<>(provider.provideInsertionPoints(base));
        if (base.request().hasHeader(DOCUMENT_HEADER)) {
            points.add(new DocumentHeaderInsertionPoint(base.request()));
        }
        return points;
    }

    private record DocumentHeaderInsertionPoint(HttpRequest base) implements AuditInsertionPoint {

        @Override
        public String name() {
            return "Header: " + DOCUMENT_HEADER;
        }

        @Override
        public String baseValue() {
            return base.headerValue(DOCUMENT_HEADER);
        }

        @Override
        public HttpRequest buildHttpRequestWithPayload(ByteArray payload) {
            return base.withUpdatedHeader(DOCUMENT_HEADER, payload.toString());
        }

        @Override
        public List<Range> issueHighlights(ByteArray payload) {
            return List.of();
        }

        @Override
        public AuditInsertionPointType type() {
            return AuditInsertionPointType.HEADER;
        }
    }
}
