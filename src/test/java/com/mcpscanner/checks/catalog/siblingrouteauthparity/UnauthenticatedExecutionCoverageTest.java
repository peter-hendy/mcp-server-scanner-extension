package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.shared.stream.ScriptedEventStreams;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.catalog.unauthenticatedrequestexecution.UnauthenticatedRequestExecutionCheck;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.request;
import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.toolsList;
import static org.assertj.core.api.Assertions.assertThat;

class UnauthenticatedExecutionCoverageTest {

    private static final Map<String, String> AUTHENTICATED =
            Map.of("Content-Type", "application/json", "Authorization", SimulatedMcpServer.VALID_TOKEN);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @ParameterizedTest
    @CsvSource({"/mcp_message,false", "/mcp/,false", "/mcp_message,true", "/mcp/,true"})
    void exactlyOneCheckReportsAnOpenRouteThatUnauthenticatedExecutionSweeps(String route, boolean appPageFallback) {
        List<AuditIssue> unauthenticatedExecution = audit(route, appPageFallback, true);
        List<AuditIssue> siblingRoute = audit(route, appPageFallback, false);

        assertThat(unauthenticatedExecution.size() + siblingRoute.size()).isEqualTo(1);
        assertThat(appPageFallback ? siblingRoute : unauthenticatedExecution).hasSize(1);
    }

    private static List<AuditIssue> audit(String route, boolean appPageFallback, boolean unauthenticatedExecution) {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().withUnguardedPath(route);
        if (appPageFallback) {
            server.answeringUnknownPathsWithAnAppPage();
        }
        HttpRequestResponse base = server.exchange(request(toolsList(), AUTHENTICATED));
        return unauthenticatedExecution
                ? new UnauthenticatedRequestExecutionCheck(McpEventLog.noop(), () -> new BearerTokenAuthStrategy("valid-token"),
                        Optional::empty, ScriptedEventStreams.none()).audit(base, server.http())
                : new SiblingRouteAuthParityCheck(McpEventLog.noop(), () -> new BearerTokenAuthStrategy("valid-token"),
                        Optional::empty).audit(base, server.http());
    }
}
