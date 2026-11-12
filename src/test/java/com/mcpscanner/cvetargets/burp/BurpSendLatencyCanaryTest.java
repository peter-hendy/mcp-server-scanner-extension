package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.SendCanaryCommand;
import com.mcpscanner.automation.SendCanaryReport;
import com.mcpscanner.cvetargets.environment.Pause;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("burp-canary")
class BurpSendLatencyCanaryTest {

    private static final int SEQUENTIAL_SENDS = 50;
    private static final Duration SEQUENTIAL_MEDIAN_BUDGET = Duration.ofMillis(50);
    private static final int CONCURRENT_SENDERS = 8;
    private static final int SENDS_PER_SENDER = 25;
    private static final double MINIMUM_EFFECTIVE_PARALLELISM = 4.0;

    private static HeadlessBurpSession burp;
    private static KeepAliveOrigin origin;

    @BeforeAll
    static void startBurpAndOrigin() {
        origin = KeepAliveOrigin.start();
        burp = new HeadlessBurpLauncher(BurpSettings.fromSystemProperties(), Pause.sleeping())
                .start("send-latency-canary", origin.immediateUrl(), () -> false);
    }

    @AfterAll
    static void stopBurpAndOrigin() {
        if (burp != null) {
            burp.close();
        }
        origin.close();
    }

    @Test
    void sequentialSendsToAKeepAliveOriginDoNotWaitForBurpsConnectionCloseTimeout() {
        SendCanaryReport report = burp.automation()
                .sendCanary(new SendCanaryCommand(origin.immediateUrl(), SEQUENTIAL_SENDS, 1));
        LatencySummary latencies = LatencySummary.ofMicros(report.latenciesMicros());
        System.out.println("Sequential extension sends through Burp: " + latencies);

        assertThat(report.unanswered()).isZero();
        assertThat(latencies.median()).as(latencies.toString()).isLessThanOrEqualTo(SEQUENTIAL_MEDIAN_BUDGET);
    }

    @Test
    void concurrentSendsToOneOriginRunInParallelRatherThanQueueingOnOneConnection() {
        SendCanaryReport report = burp.automation().sendCanary(new SendCanaryCommand(origin.delayedUrl(),
                CONCURRENT_SENDERS * SENDS_PER_SENDER, CONCURRENT_SENDERS));
        Duration wall = Duration.of(report.wallMicros(), ChronoUnit.MICROS);
        double effectiveParallelism = (double) KeepAliveOrigin.DELAYED_REPLY
                .multipliedBy(report.latenciesMicros().size()).toNanos() / wall.toNanos();
        System.out.printf("Concurrent extension sends through Burp: %d x %d sends to a %d ms origin in %d ms "
                        + "(effective parallelism %.1f; %s)%n", CONCURRENT_SENDERS, SENDS_PER_SENDER,
                KeepAliveOrigin.DELAYED_REPLY.toMillis(), wall.toMillis(), effectiveParallelism,
                LatencySummary.ofMicros(report.latenciesMicros()));

        assertThat(report.unanswered()).isZero();
        assertThat(effectiveParallelism).as("sends in flight on average").isGreaterThanOrEqualTo(
                MINIMUM_EFFECTIVE_PARALLELISM);
    }
}
