package com.mcpscanner.integration;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRealHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class OAuthAuthorizationConsentCheckIT {

    private static final String AUTHORIZATION = "MCP OAuth authorization without login or consent";
    private static final String REUSE = "MCP OAuth authorization code redeemed more than once";

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void anAutoApprovingServerIssuesATokenThatOpensTheEndpoint() throws Exception {
        List<AuditIssue> issues = scan("--consent", "auto", "--default-mode", "safe");

        assertThat(issues).extracting(AuditIssue::name).containsExactly(AUTHORIZATION);
        assertThat(issues.get(0).severity()).isEqualTo(AuditIssueSeverity.HIGH);
    }

    @Test
    void aSessionlessServerIsJudgedTheSameWay() throws Exception {
        List<AuditIssue> issues = scan("--consent", "auto", "--default-mode", "safe", "--sessions", "off");

        assertThat(issues).extracting(AuditIssue::name).containsExactly(AUTHORIZATION);
        assertThat(issues.get(0).severity()).isEqualTo(AuditIssueSeverity.HIGH);
    }

    @Test
    void aConsentPageKeepsTheCheckSilent() throws Exception {
        assertThat(scan("--consent", "prompt", "--default-mode", "safe")).isEmpty();
    }

    @ParameterizedTest(name = "{0} reports {1} reuse")
    @CsvSource({"code_reuse, sequential", "code_redemption_race, concurrent"})
    void aReusableCodeIsReportedOnlyByItsVulnerableModule(String module, String shape) throws Exception {
        List<AuditIssue> vulnerable = scan("--consent", "auto", "--default-mode", "safe", "--vuln", module + "=vulnerable");

        assertThat(vulnerable).extracting(AuditIssue::name).containsExactlyInAnyOrder(AUTHORIZATION, REUSE);
        assertThat(vulnerable).filteredOn(issue -> issue.name().equals(REUSE)).singleElement()
                .satisfies(issue -> assertThat(issue.detail()).contains(shape));
        assertThat(scan("--consent", "auto", "--default-mode", "safe", "--vuln", module + "=safe"))
                .extracting(AuditIssue::name).containsExactly(AUTHORIZATION);
    }

    private static List<AuditIssue> scan(String... serverArguments) throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(serverArguments)) {
            McpCheck check = CheckCatalog.discover(CheckServices.standalone()).find("oauth-authorization-consent")
                    .orElseThrow();
            CheckRunner runner = new CheckRunner(new RecordingRealHttp(),
                    new McpInsertionPointProvider()::provideInsertionPoints);
            return new ArrayList<>(runner.run(check, List.of(pingAgainst(server.port()))));
        }
    }

    private static HttpRequestResponse pingAgainst(int port) {
        HttpRequest ping = HttpRequest.httpRequestFromUrl(McpCheckItSupport.mcpEndpoint(port))
                .withMethod("POST")
                .withAddedHeader("Content-Type", "application/json")
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");
        return HttpRequestResponse.httpRequestResponse(ping, null);
    }
}
