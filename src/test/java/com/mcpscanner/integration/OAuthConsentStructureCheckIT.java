package com.mcpscanner.integration;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRealHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class OAuthConsentStructureCheckIT {

    private static final String CHECK_ID = "oauth-consent-structure-reflection";
    private static final String CLIENT_NAME_ISSUE = "OAuth authorization flow page renders client_name as HTML structure";
    private static final String STATE_ISSUE = "OAuth authorization flow page renders state as HTML structure";

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    static Stream<Arguments> modes() {
        return Stream.of(
                Arguments.of("vulnerable", "vulnerable", List.of(CLIENT_NAME_ISSUE, STATE_ISSUE)),
                Arguments.of("vulnerable", "safe", List.of(CLIENT_NAME_ISSUE)),
                Arguments.of("safe", "vulnerable", List.of(STATE_ISSUE)),
                Arguments.of("safe", "safe", List.of()));
    }

    @ParameterizedTest(name = "client_name={0} state={1}")
    @MethodSource("modes")
    void firesOnlyForTheVulnerableReflections(String clientNameMode, String stateMode, List<String> expected)
            throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(
                "--default-mode", "safe",
                "--vuln", "unauthenticated_execution=vulnerable",
                "--vuln", "consent_client_name_reflection=" + clientNameMode,
                "--vuln", "consent_state_reflection=" + stateMode)) {

            RecordingRealHttp http = new RecordingRealHttp(McpCheckItSupport.realHttpClient());
            List<AuditIssue> issues = run(http, server.port());

            assertThat(http.sentUrls).as("the consent page must be reached, so silence is not vacuous")
                    .anyMatch(url -> url.contains("/authorize?") && url.contains("client_id="));

            assertThat(issues).extracting(AuditIssue::name).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(issues).allSatisfy(issue -> {
                assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.INFORMATION);
                assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.TENTATIVE);
                assertThat(issue.detail()).contains("No enforced Content-Security-Policy");
            });
        }
    }

    private static List<AuditIssue> run(RecordingRealHttp http, int port) {
        McpCheck check = CheckCatalog.discover(CheckServices.standalone()).find(CHECK_ID).orElseThrow();
        HttpRequest toolsList = HttpRequest.httpRequestFromUrl(McpCheckItSupport.mcpEndpoint(port))
                .withMethod("POST")
                .withAddedHeader("Content-Type", "application/json")
                .withAddedHeader("Accept", "application/json, text/event-stream")
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}");
        HttpRequestResponse base = http.sendRequest(toolsList);
        return new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints)
                .run(check, List.of(base));
    }
}
