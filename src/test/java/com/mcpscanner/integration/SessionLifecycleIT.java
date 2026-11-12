package com.mcpscanner.integration;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class SessionLifecycleIT {

    private static final String CHECK_ID = "session-lifecycle";
    private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/list\",\"params\":{}}";
    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"ping\"}";

    private final Http http = new VerbatimHeaderHttp();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void safeSessionsStaySilentAndLeaveTheLiveSessionWorking() throws Exception {
        assertThat(scan()).isEmpty();
    }

    @Test
    void sequentialSessionIdsAreLowSeverity() throws Exception {
        assertThat(scan("session_guessable_ids=vulnerable")).singleElement().satisfies(issue -> {
            assertThat(issue.name()).isEqualTo("Guessable MCP session IDs");
            assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.LOW);
        });
    }

    @Test
    void sequentialSessionIdsThatAuthenticateAloneAreHighSeverity() throws Exception {
        assertThat(scan("session_guessable_ids=vulnerable", "auth_session_credential=vulnerable")).singleElement()
                .satisfies(issue -> assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.HIGH));
    }

    @Test
    void aSessionThatSurvivesDeleteIsReported() throws Exception {
        assertThat(scan("session_delete_ignored=vulnerable")).singleElement()
                .satisfies(issue -> assertThat(issue.name()).isEqualTo("MCP session usable after DELETE"));
    }

    private List<AuditIssue> scan(String... vulns) throws Exception {
        List<String> arguments = new ArrayList<>(List.of("--default-mode", "safe"));
        for (String vuln : vulns) {
            arguments.add("--vuln");
            arguments.add(vuln);
        }
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(arguments.toArray(String[]::new))) {
            String token = McpCheckItSupport.mintTestToken(server.port());
            String liveSession = McpCheckItSupport.initializeSessionWithBearer(server.port(), token);
            HttpRequestResponse base = send(server.mcpEndpoint(), token, liveSession, TOOLS_LIST);
            McpCheck check = CheckCatalog.discover(new CheckServices(McpEventLog.noop(),
                    () -> new BearerTokenAuthStrategy(token), Optional::empty, Optional::empty,
                    PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown()))
                    .find(CHECK_ID).orElseThrow();
            List<AuditIssue> issues = new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints)
                    .run(check, List.of(base));
            assertThat(send(server.mcpEndpoint(), token, liveSession, PING).response().bodyToString())
                    .as("live session after the audit").contains("\"result\"");
            return issues;
        }
    }

    private HttpRequestResponse send(String endpoint, String token, String session, String body) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream")
                .withHeader("MCP-Protocol-Version", "2025-11-25")
                .withHeader("Authorization", "Bearer " + token)
                .withHeader("Mcp-Session-Id", session)
                .withBody(body);
        HttpRequestResponse exchange = http.sendRequest(request);
        assertThat(exchange.response().statusCode()).isBetween((short) 200, (short) 299);
        return exchange;
    }
}
