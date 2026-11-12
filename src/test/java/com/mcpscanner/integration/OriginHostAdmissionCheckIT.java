package com.mcpscanner.integration;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class OriginHostAdmissionCheckIT {

    private static final String ORIGIN = "MCP Origin header admission";
    private static final String HOST = "MCP Host header admission";
    private static final String PAIR = "MCP Host and Origin header admission";

    @BeforeAll
    static void installMontoya() {
        MontoyaTestFactory.install();
    }

    @ParameterizedTest(name = "origin={0} host={1} reply={2}")
    @CsvSource(delimiter = '|', value = {
            "vulnerable | vulnerable | json | " + ORIGIN + ";" + HOST + ";" + PAIR,
            "vulnerable | safe       | json | " + ORIGIN,
            "safe       | vulnerable | json | " + HOST,
            "safe       | safe       | json | ",
            "vulnerable | vulnerable | sse  | " + ORIGIN + ";" + HOST + ";" + PAIR,
            "safe       | safe       | sse  | "})
    void reportsExactlyTheHeadersTheServerAdmits(String originMode, String hostMode, String replyMode,
                                                String expectedIssues) throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(
                "--default-mode", "safe",
                "--vuln", "unauthenticated_execution=vulnerable",
                "--vuln", "origin_admission=" + originMode,
                "--vuln", "host_admission=" + hostMode,
                "--response-mode", replyMode)) {
            VerbatimHeaderHttp http = new VerbatimHeaderHttp();
            HttpRequestResponse baseline = http.sendRequest(toolsList(server.port()));

            List<AuditIssue> issues = new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints)
                    .run(check(), List.of(baseline));

            assertThat((int) baseline.response().statusCode()).isEqualTo(200);
            assertThat(issues).extracting(AuditIssue::name).containsExactlyElementsOf(names(expectedIssues));
            assertThat(issues).allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(
                    issue.name().equals(PAIR) ? AuditIssueSeverity.MEDIUM : AuditIssueSeverity.INFORMATION));
        }
    }

    private static McpCheck check() {
        return CheckCatalog.discover(CheckServices.standalone()).find("origin-host-admission").orElseThrow();
    }

    private static HttpRequest toolsList(int port) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/event-stream");
        String session = McpCheckItSupport.initializeSession(port);
        if (session != null) {
            headers.put("Mcp-Session-Id", session);
        }
        return ImmutableHttpRequest.post(McpCheckItSupport.mcpEndpoint(port), headers,
                "{\"jsonrpc\":\"2.0\",\"id\":41,\"method\":\"tools/list\",\"params\":{}}");
    }

    private static List<String> names(String expected) {
        return expected == null || expected.isBlank() ? List.of() : List.of(expected.split(";"));
    }
}
