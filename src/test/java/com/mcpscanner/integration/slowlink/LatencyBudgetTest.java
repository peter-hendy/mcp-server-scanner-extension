package com.mcpscanner.integration.slowlink;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatencyBudgetTest {

    private static final Duration RTT = Duration.ofMillis(200);
    private static final Slack SLACK = new Slack(Duration.ofMillis(500), Duration.ofMillis(10));

    @Test
    void allowsOneRoundTripPerConcurrentWaveOfRequestsPlusFixedAndPerRequestSlack() {
        assertThat(new LatencyBudget("scan", 9, RTT, 4, SLACK).allowed()).isEqualTo(Duration.ofMillis(1190));
    }

    @Test
    void passesAnOperationThatFinishesWithinItsBudget() {
        assertThatCode(() -> new LatencyBudget("connect", 3, RTT, 1, SLACK).assertMet(Duration.ofMillis(1130)))
                .doesNotThrowAnyException();
    }

    @Test
    void failsAHiddenWaitWithTheMeasuredTimeRequestCountAndBudget() {
        assertThatThrownBy(() -> new LatencyBudget("connect", 3, RTT, 1, SLACK).assertMet(Duration.ofMillis(2130)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("connect took 2130 ms")
                .hasMessageContaining("3 requests")
                .hasMessageContaining("budget 1130 ms");
    }
}
