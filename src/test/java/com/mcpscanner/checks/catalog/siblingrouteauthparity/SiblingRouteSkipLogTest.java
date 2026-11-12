package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.request;
import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.toolsList;
import static org.assertj.core.api.Assertions.assertThat;

class SiblingRouteSkipLogTest {

    private static final Map<String, String> AUTHENTICATED =
            Map.of("Content-Type", "application/json", "Authorization", SimulatedMcpServer.VALID_TOKEN);
    private static final String SUBJECT = "tools/list at " + request(toolsList(), AUTHENTICATED).url() + " (";
    private static final String STOPPED = "Scan check: sibling-route-auth-parity — stopped " + SUBJECT;
    private static final String SKIPPED = "Scan check: sibling-route-auth-parity — skipped " + SUBJECT;

    private final McpEventLog eventLog = new McpEventLog(null);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void logsThatTheRequestBudgetStoppedTheSweep() {
        audit(SimulatedMcpServer.enforcing().withUnguardedPath("/sse"), bearer(4));

        assertThat(messages()).contains(STOPPED + "request budget used up: 4 probe requests)");
    }

    @Test
    void logsThatARateLimitStoppedTheSweep() {
        audit(SimulatedMcpServer.enforcing().lockingOutAfterCredentialFailures(2), bearer(
                SiblingRouteAuthParityCheck.REQUEST_BUDGET));

        assertThat(messages()).contains(STOPPED + "rate limited: the server answered HTTP 429)");
    }

    @Test
    void logsThatAFailedAuthenticatedBaselineStoppedTheCheck() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().withUnguardedPath("/messages");
        HttpRequestResponse base = server.exchange(request(toolsList(), Map.of("Content-Type", "application/json")));

        new SiblingRouteAuthParityCheck(eventLog, NoAuthStrategy::new, Optional::empty).audit(base, server.http());

        assertThat(messages()).contains(SKIPPED
                + "prerequisite missing: the authenticated catalog listing did not succeed)");
    }

    @Test
    void logsThatAMainRouteAnsweringWithoutCredentialsLeavesTheHostToTheOtherCheck() {
        audit(SimulatedMcpServer.open().withUnguardedPath("/messages"), bearer(
                SiblingRouteAuthParityCheck.REQUEST_BUDGET));

        assertThat(messages()).contains(SKIPPED + "not applicable: the main route did not refuse an anonymous "
                + "listing, so there is no refusal to compare sibling routes with)");
    }

    @Test
    void logsNothingWhenEverySiblingRouteWasTried() {
        audit(SimulatedMcpServer.enforcing(), bearer(SiblingRouteAuthParityCheck.REQUEST_BUDGET));

        assertThat(messages()).isEmpty();
    }

    private SiblingRouteAuthParityCheck bearer(int budget) {
        return new SiblingRouteAuthParityCheck(eventLog, () -> new BearerTokenAuthStrategy("valid-token"),
                Optional::empty, budget);
    }

    private static void audit(SimulatedMcpServer server, SiblingRouteAuthParityCheck check) {
        check.audit(server.exchange(request(toolsList(), AUTHENTICATED)), server.http());
    }

    private List<String> messages() {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message).toList();
    }
}
