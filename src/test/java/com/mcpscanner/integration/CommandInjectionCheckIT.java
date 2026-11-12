package com.mcpscanner.integration;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.cvetargets.run.ArmLateIssues;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.LoopbackCollaborator;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class CommandInjectionCheckIT {

    @BeforeAll
    static void installMontoya() {
        MontoyaTestFactory.install();
    }

    @ParameterizedTest(name = "{0}={1} via {2}.{3}")
    @CsvSource(delimiter = '|', value = {
            "shell_command_injection        | vulnerable | ping_host       | host       | MCP OS command injection | shell arithmetic expansion",
            "shell_command_injection        | safe       | ping_host       | host       |                          | ",
            "blind_shell_command_injection  | vulnerable | archive_logs    | label      | MCP OS command injection | Collaborator HTTP interaction",
            "blind_shell_command_injection  | safe       | archive_logs    | label      |                          | ",
            "python_eval_injection          | vulnerable | calculate       | expression | MCP code execution argument | Python expression",
            "python_eval_injection          | safe       | calculate       | expression |                          | ",
            "javascript_eval_injection      | vulnerable | render_greeting | template   | MCP code injection       | JavaScript template literal",
            "javascript_eval_injection      | safe       | render_greeting | template   |                          | "})
    void firesOnlyOnTheVulnerableModeOfEachInjectionModule(String vulnerability, String mode, String tool,
                                                          String argument, String expectedIssue,
                                                          String technique) throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(
                "--default-mode", "safe",
                "--vuln", "unauthenticated_execution=vulnerable",
                "--vuln", vulnerability + "=" + mode,
                "--command-timeout", "10");
             LoopbackCollaborator collaborator = LoopbackCollaborator.start()) {
            VerbatimHeaderHttp http = new VerbatimHeaderHttp();
            HttpRequestResponse baseline = http.sendRequest(toolCall(server.port(), tool, argument));

            ArmLateIssues late = new ArmLateIssues();
            List<AuditIssue> issues = new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints,
                    late).run(check(collaborator, late), List.of(baseline));
            late.close();

            assertThat((int) baseline.response().statusCode()).isEqualTo(200);
            assertThat(issues).extracting(AuditIssue::name)
                    .containsExactlyElementsOf(expectedIssue == null ? List.of() : List.of(expectedIssue));
            assertThat(issues).allSatisfy(issue -> assertThat(issue.detail()).contains(tool)
                    .contains("params.arguments." + argument).contains("Technique: " + technique));
        }
    }

    private static McpCheck check(CollaboratorClient collaborator, ArmLateIssues late) {
        CheckServices services = late.collectingFor(new CheckServices(McpEventLog.noop(), CheckServices.standalone().authStrategy(),
                () -> Optional.of(collaborator), Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()));
        return CheckCatalog.discover(services).find("command-injection").orElseThrow();
    }

    private static HttpRequest toolCall(int port, String tool, String argument) throws Exception {
        HttpRequest request = HttpRequest.httpRequestFromUrl(McpCheckItSupport.mcpEndpoint(port)).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream")
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool
                        + "\",\"arguments\":{\"" + argument + "\":\"nightly\"}}}");
        String session = McpCheckItSupport.initializeSession(port);
        return session == null ? request : request.withHeader("Mcp-Session-Id", session);
    }
}
