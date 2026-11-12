package com.mcpscanner.cvetargets.run;

import com.mcpscanner.testutil.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class ArmDeadlineTest {

    private final ArmDeadline deadline = new ArmDeadline();

    @Test
    void returnsWhatTheWorkReturnsWithinTheLimit() {
        assertThat(deadline.within(Duration.ofSeconds(5), () -> "done")).isEqualTo("done");
    }

    @Test
    void rethrowsTheWorksOwnFailure() {
        assertThatThrownBy(() -> deadline.within(Duration.ofSeconds(5), () -> {
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("boom");
    }

    @Test
    void timesOutWorkThatOutlastsTheLimitWithoutInterruptingIt() {
        CountDownLatch released = new CountDownLatch(1);
        AtomicBoolean finishedUninterrupted = new AtomicBoolean();

        assertThatThrownBy(() -> deadline.within(Duration.ofMillis(100), () -> {
            released.await();
            finishedUninterrupted.set(!Thread.currentThread().isInterrupted());
            return "late";
        })).isInstanceOf(ArmTimedOut.class).hasMessage("arm timed out after 0.1 s");
        released.countDown();
        await().atMost(Duration.ofSeconds(5)).untilTrue(finishedUninterrupted);
    }

    @Test
    void countsWallClockTimeTheMonotonicClockMissedWhileTheMachineSlept() {
        CountDownLatch released = new CountDownLatch(1);
        MutableClock wallClock = new MutableClock(Instant.parse("2026-11-12T12:00:00Z"));
        ArmDeadline sleepAware = new ArmDeadline(wallClock, Duration.ofMillis(10));

        assertThatThrownBy(() -> sleepAware.within(Duration.ofMinutes(20), () -> {
            wallClock.advance(Duration.ofMinutes(75));
            released.await();
            return "late";
        })).isInstanceOf(ArmTimedOut.class).hasMessage("arm timed out after 20 min");
        released.countDown();
    }

    @Test
    void describesTheLimitInMinutesWhenItIsWholeMinutes() {
        assertThat(new ArmTimedOut(Duration.ofMinutes(20))).hasMessage("arm timed out after 20 min");
    }
}
