package com.mcpscanner.integration;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class DownstreamRouteEscapeCheckIT {

    private static final Pattern SUBJECT = Pattern.compile("Request: ([^<]+)</p><p>Insertion point: ([^<]+)<");
    private static final String NO_AUTH = "unauthenticated_execution=vulnerable";

    @BeforeAll
    static void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void firesOnEachApiRootReachableRouteVariantWhenVulnerable() throws Exception {
        List<String> fields = reportedFields("--default-mode", "safe", "--vuln", NO_AUTH,
                "--vuln", "downstream_raw_segment=vulnerable", "--vuln", "downstream_encoded_dot_segment=vulnerable",
                "--vuln", "downstream_route_escape=vulnerable");

        assertThat(fields).containsExactlyInAnyOrder(
                "tools/call get_invoice | MCP field: params.arguments.invoice_id",
                "tools/call get_order | MCP field: params.arguments.order_id");
    }

    @Test
    void staysSilentWhenEveryRouteVariantIsSafe() throws Exception {
        assertThat(reportedFields("--default-mode", "safe", "--vuln", NO_AUTH)).isEmpty();
    }

    @Test
    void alsoReportsDotSegmentResolvingFilePathsWhenEveryVulnerabilityIsVulnerable() throws Exception {
        List<String> fields = reportedFields("--default-mode", "vulnerable");

        assertThat(fields).contains(
                "tools/call get_invoice | MCP field: params.arguments.invoice_id",
                "tools/call get_order | MCP field: params.arguments.order_id");
        assertThat(fields).filteredOn(field -> !field.contains("get_invoice") && !field.contains("get_order"))
                .isNotEmpty()
                .allMatch(field -> field.contains("review_file_") || field.contains("file:///"));
    }

    private static List<String> reportedFields(String... serverArgs) throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(serverArgs)) {
            TemplateExpansionRegistry templateExpansions = new TemplateExpansionRegistry();
            ArmEndpoint endpoint = new ArmEndpoint(URI.create(McpCheckItSupport.mcpEndpoint(server.port())),
                    new NoAuthStrategy());
            VerbatimHeaderHttp http = new VerbatimHeaderHttp();
            try (BaseRequestSet requests = new DiscoveredInventoryRequestSource(
                    new GeneratedRequestPlanner(templateExpansions)).open(endpoint, BaseRequestScope.unlimited(List.of()), http)) {
                McpCheck check = CheckCatalog.discover(CheckServices.standalone())
                        .find("downstream-route-escape").orElseThrow();
                List<AuditIssue> issues = new CheckRunner(requests.route(http),
                        new McpInsertionPointProvider(() -> true, templateExpansions, BurpNativeJsonRpcFields.ABSENT)::provideInsertionPoints)
                        .run(check, requests.requests());
                return issues.stream().map(DownstreamRouteEscapeCheckIT::field).toList();
            }
        }
    }

    private static String field(AuditIssue issue) {
        Matcher matcher = SUBJECT.matcher(issue.detail());
        return matcher.find() ? matcher.group(1) + " | " + matcher.group(2) : issue.detail();
    }
}
