package com.mcpscanner.integration;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.requests.BaseRequestSet;
import com.mcpscanner.cvetargets.requests.BaseRequestScope;
import com.mcpscanner.cvetargets.requests.DiscoveredInventoryRequestSource;
import com.mcpscanner.cvetargets.requests.GeneratedRequestPlanner;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.cvetargets.run.ArmLateIssues;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.scan.field.TemplateExpansionRegistry;
import com.mcpscanner.testutil.JsonParameterInsertionPoint;
import com.mcpscanner.testutil.LoopbackCollaborator;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class OutboundRequestCheckIT {

    private static final List<String> OUTBOUND_VULNS = List.of("fetch_tool_arguments", "fetch_resource_uri",
            "fetch_list_cursor", "fetch_meta", "fetch_client_info", "fetch_headers", "fetch_oauth_client_metadata");
    private static final String REGISTRATION = "{\"redirect_uris\":[\"http://127.0.0.1:5999/cb\"],"
            + "\"client_name\":\"scanner\",\"token_endpoint_auth_method\":\"none\","
            + "\"client_uri\":\"https://client.example.test\",\"logo_uri\":\"https://client.example.test/logo.png\","
            + "\"jwks_uri\":\"https://client.example.test/jwks.json\"}";

    private static final String FIELD_PREFIX = "<p>Field: ";
    private static final List<String> HANDSHAKE_ERA = List.of("--protocol-version", "2025-11-25");
    private static final List<String> EVERY_VERSION_NEGOTIATING_STATELESS = List.of();

    @BeforeAll
    static void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void firesOnEveryOutboundFieldKindWhenVulnerable() throws Exception {
        List<AuditIssue> issues = scan(HANDSHAKE_ERA, "vulnerable");

        assertThat(locations(issues)).contains(
                "MCP field: params.arguments.url in tools/call fetch_document",
                "MCP field: params.arguments.options.timeout_seconds in tools/call fetch_document",
                "MCP field: params.arguments.options.tags in tools/call fetch_document",
                "MCP field: params.uri",
                "MCP field: params.cursor",
                "MCP field: params._meta.progressToken",
                "MCP header: X-Upstream-Url",
                "MCP header: Mcp-Session-Id",
                "OAuth parameter client_uri in POST /register",
                "OAuth parameter logo_uri in POST /register",
                "OAuth parameter jwks_uri in POST /register")
                .anyMatch(location -> location.startsWith("MCP field: params.capabilities."))
                .noneMatch(location -> location.startsWith("MCP field: params.cursor in "));
        assertThat(issues).filteredOn(issue -> location(issue).contains("params.clientInfo."))
                .isNotEmpty().allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.INFORMATION));
        assertThat(issues).filteredOn(issue -> location(issue).contains("tools/call fetch_document"))
                .allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.MEDIUM));
    }

    @Test
    void staysSilentWhenEveryOutboundFieldIsSafe() throws Exception {
        assertThat(scan(HANDSHAKE_ERA, "safe")).isEmpty();
    }

    @Test
    void firesOnEveryStatelessFieldKindWhenVulnerable() throws Exception {
        List<AuditIssue> issues = scan(EVERY_VERSION_NEGOTIATING_STATELESS, "vulnerable");

        assertThat(locations(issues)).contains(
                "MCP field: params.arguments.url in tools/call fetch_document",
                "MCP field: params.uri",
                "MCP field: params.cursor",
                "MCP field: params._meta.progressToken",
                "MCP header: X-Upstream-Url",
                "OAuth parameter client_uri in POST /register")
                .noneMatch(location -> location.startsWith("MCP field: params.uri in "));
        assertThat(issues).filteredOn(issue -> location(issue).contains("modelcontextprotocol/clientInfo."))
                .isNotEmpty().allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.INFORMATION));
        assertThat(issues).filteredOn(issue -> location(issue).contains("params.arguments."))
                .isNotEmpty().allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.MEDIUM));
    }

    @Test
    void staysSilentOnTheStatelessEraWhenEveryOutboundFieldIsSafe() throws Exception {
        assertThat(scan(EVERY_VERSION_NEGOTIATING_STATELESS, "safe")).isEmpty();
    }

    private static List<AuditIssue> scan(List<String> servedVersions, String mode) throws Exception {
        List<String> args = new ArrayList<>(servedVersions);
        args.addAll(List.of("--default-mode", "safe", "--vuln", "unauthenticated_execution=vulnerable"));
        OUTBOUND_VULNS.forEach(vuln -> args.addAll(List.of("--vuln", vuln + "=" + mode)));
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(args.toArray(String[]::new));
             LoopbackCollaborator collaborator = LoopbackCollaborator.start()) {
            ArmLateIssues late = new ArmLateIssues();
            McpCheck check = CheckCatalog.discover(late.collectingFor(new CheckServices(McpEventLog.noop(),
                    () -> new CustomHeaderAuthStrategy(Map.of()), () -> Optional.of(collaborator), Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown())))
                    .find("outbound-request").orElseThrow();
            TemplateExpansionRegistry templateExpansions = new TemplateExpansionRegistry();
            ArmEndpoint endpoint = new ArmEndpoint(URI.create(McpCheckItSupport.mcpEndpoint(server.port())),
                    new CustomHeaderAuthStrategy(Map.of("X-Upstream-Url", "https://upstream.example.test")));
            VerbatimHeaderHttp http = new VerbatimHeaderHttp();
            try (BaseRequestSet requests = new DiscoveredInventoryRequestSource(
                    new GeneratedRequestPlanner(templateExpansions)).open(endpoint, BaseRequestScope.unlimited(List.of()), http)) {
                List<AuditIssue> issues = new ArrayList<>(new CheckRunner(requests.route(http),
                        new McpInsertionPointProvider(() -> true, templateExpansions, BurpNativeJsonRpcFields.ABSENT)::provideInsertionPoints, late)
                        .run(check, requests.requests()));
                issues.addAll(new CheckRunner(http, base -> List.of(
                        new JsonParameterInsertionPoint(base.request(), "client_uri"),
                        new JsonParameterInsertionPoint(base.request(), "logo_uri"),
                        new JsonParameterInsertionPoint(base.request(), "jwks_uri")), late)
                        .run(check, List.of(registration(server.port(), http))));
                return issues;
            } finally {
                late.close();
            }
        }
    }

    private static HttpRequestResponse registration(int port, VerbatimHeaderHttp http) {
        HttpRequest request = HttpRequest.httpRequestFromUrl("http://127.0.0.1:" + port + "/register")
                .withMethod("POST").withHeader("Content-Type", "application/json").withBody(REGISTRATION);
        return http.sendRequest(request);
    }

    private static List<String> locations(List<AuditIssue> issues) {
        return issues.stream().map(OutboundRequestCheckIT::location).toList();
    }

    private static String location(AuditIssue issue) {
        String detail = issue.detail();
        return detail.substring(FIELD_PREFIX.length(), detail.indexOf("</p>"));
    }
}
