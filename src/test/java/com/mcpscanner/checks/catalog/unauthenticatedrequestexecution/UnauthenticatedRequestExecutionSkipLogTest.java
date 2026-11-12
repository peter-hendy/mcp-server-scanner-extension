package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.checks.shared.stream.ScriptedEventStreams;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.request;
import static com.mcpscanner.checks.shared.probe.SimulatedMcpServer.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

class UnauthenticatedRequestExecutionSkipLogTest {

    private static final String PREFIX = "Scan check: unauthenticated-request-execution — ";
    private static final Map<String, String> AUTHENTICATED =
            Map.of("Content-Type", "application/json", "Authorization", SimulatedMcpServer.VALID_TOKEN);
    private static final String SUBJECT = "tools/call read_secret at " + request(toolCall(), AUTHENTICATED).url();
    private static final String RATE_LIMITED = " (rate limited: the server answered HTTP 429)";

    private final McpEventLog eventLog = new McpEventLog(null);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void logsWhyARequestThatIsNotAJsonRpcPostIsSkipped() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();

        audit(server, request(toolCall(), AUTHENTICATED).withMethod("GET"));

        assertThat(messages()).containsExactly(PREFIX + "skipped " + SUBJECT
                + " (not applicable: needs a JSON-RPC 2.0 POST)");
    }

    @Test
    void logsThatARateLimitStoppedTheProbing() {
        audit(SimulatedMcpServer.enforcing().lockingOutAfterCredentialFailures(1), request(toolCall(), AUTHENTICATED));

        assertThat(messages()).contains(PREFIX + "stopped " + SUBJECT + RATE_LIMITED);
    }

    @Test
    void logsThatARateLimitStoppedTheForwardedClientTier() {
        audit(SimulatedMcpServer.enforcing().throttlingForwardedHeaders(), request(toolCall(), AUTHENTICATED));

        assertThat(messages()).contains(PREFIX + "stopped the forwarded-client tier on " + SUBJECT + RATE_LIMITED);
    }

    @Test
    void logsNothingWhenEveryTierFinished() {
        audit(SimulatedMcpServer.enforcing(), request(toolCall(), AUTHENTICATED));

        assertThat(messages()).isEmpty();
    }

    private void audit(SimulatedMcpServer server, HttpRequest request) {
        new UnauthenticatedRequestExecutionCheck(eventLog, () -> new BearerTokenAuthStrategy("valid-token"),
                Optional::empty, ScriptedEventStreams.none()).audit(server.exchange(request), server.http());
    }

    private List<String> messages() {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message).toList();
    }
}
