package com.mcpscanner.checks.api.crossidentity;

import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ProbeTransportTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void directRequestsStopAtTheBudgetWhileTripletSamplesDoNot() {
        McpEventLog eventLog = new McpEventLog(null);
        FakeMcpTarget target = new FakeMcpTarget(request -> FakeReply.result("ok"));
        ProbeTransport transport = new ProbeTransport(target.http(),
                new DecisionLog(new CheckLog(eventLog, "session-binding"), "tools/call t"));
        var request = FakeMcpTarget.toolCall("t", "{}", "A");

        for (int index = 0; index < ProbeTransport.DIRECT_REQUEST_BUDGET; index++) {
            assertThat(transport.send(request)).isNotNull();
        }

        assertThat(transport.send(request)).isNull();
        assertThat(transport.sample(request)).isNull();
        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::level, McpEventLog.LogEntry::message)
                .containsExactly(
                        tuple(McpEventLog.Level.INFO, "Scan check: session-binding — stopped tools/call t "
                                + "(request budget used up: 40 direct requests per identity)"));
    }

    @Test
    void aFreshBudgetIsIndependentOfTheExhaustedOne() {
        FakeMcpTarget target = new FakeMcpTarget(request -> FakeReply.result("ok"));
        DecisionLog log = new DecisionLog(new CheckLog(McpEventLog.noop(), "session-binding"), "tools/call t");
        ProbeTransport shared = new ProbeTransport(target.http(), log);
        var request = FakeMcpTarget.toolCall("t", "{}", "A");
        for (int index = 0; index <= ProbeTransport.DIRECT_REQUEST_BUDGET; index++) {
            shared.send(request);
        }

        assertThat(shared.withFreshBudget(log.about("b")).send(request)).isNotNull();
    }
}
