package com.mcpscanner.checks.shared.collaborator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ScheduledWatchTickerTest {

    @Test
    void ticksOnADaemonThreadUntilStopped() {
        ScheduledWatchTicker ticker = new ScheduledWatchTicker();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger daemonTicks = new AtomicInteger();

        ticker.start(() -> {
            ticks.incrementAndGet();
            if (Thread.currentThread().isDaemon()) {
                daemonTicks.incrementAndGet();
            }
        }, Duration.ofMillis(5));
        await().atMost(Duration.ofSeconds(5)).until(() -> ticks.get() >= 2);
        ticker.stop();
        int afterStop = ticks.get();

        await().pollDelay(Duration.ofMillis(50)).atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(ticks.get()).isLessThanOrEqualTo(afterStop + 1));
        assertThat(daemonTicks.get()).isEqualTo(ticks.get());
    }
}
