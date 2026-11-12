package com.mcpscanner.integration.slowlink;

import java.time.Duration;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public final class RelayStopwatch {

    private static final Duration TARGET_LOG_SETTLE = Duration.ofSeconds(10);

    private final DelayingRelay relay;
    private final TargetRequestLog target;

    public RelayStopwatch(DelayingRelay relay, TargetRequestLog target) {
        this.relay = relay;
        this.target = target;
    }

    public LatencyBudget time(String operation, int concurrency, Slack slack, Callable<?> action)
            throws Exception {
        target.clear();
        int requestsBefore = relay.requestsSeen();
        long started = System.nanoTime();
        action.call();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        int relayed = relay.requestsSeen() - requestsBefore;
        LatencyBudget budget = new LatencyBudget(operation, relayed, relay.roundTrip(), concurrency, slack);
        System.out.println("[slow-link] " + budget.describe(elapsed));
        assertEveryRequestCameThroughTheRelay(operation, relayed);
        budget.assertMet(elapsed);
        return budget;
    }

    private void assertEveryRequestCameThroughTheRelay(String operation, int relayed) {
        await().atMost(TARGET_LOG_SETTLE).untilAsserted(() -> assertThat(target.received())
                .as("%s: requests the target received vs requests the relay carried; more at the target means "
                        + "some traffic bypassed the delay", operation)
                .isEqualTo(relayed));
    }
}
