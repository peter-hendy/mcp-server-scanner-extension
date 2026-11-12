package com.mcpscanner.cvetargets.run;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class ArmScheduleTest {

    @Test
    void runsUpToItsParallelismOfArmsAtOnce() {
        CountDownLatch bothRunning = new CountDownLatch(2);
        try (ArmSchedule schedule = new ArmSchedule(2)) {
            CompletableFuture<String> first = schedule.submit(() -> awaitPeer(bothRunning, "first"));
            CompletableFuture<String> second = schedule.submit(() -> awaitPeer(bothRunning, "second"));

            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20))
                    .until(() -> first.isDone() && second.isDone());
            assertThat(first).isCompletedWithValue("first");
            assertThat(second).isCompletedWithValue("second");
        }
    }

    @Test
    void runsArmsOneAfterAnotherInSubmissionOrderWithAParallelismOfOne() {
        List<String> order = new CopyOnWriteArrayList<>();
        try (ArmSchedule schedule = new ArmSchedule(1)) {
            List<CompletableFuture<Boolean>> arms = List.of("a", "b", "c").stream()
                    .map(name -> schedule.submit(() -> order.add(name)))
                    .toList();
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20))
                    .until(() -> arms.stream().allMatch(CompletableFuture::isDone));
            assertThat(arms).allSatisfy(arm -> assertThat(arm).isCompletedWithValue(true));
        }

        assertThat(order).containsExactly("a", "b", "c");
    }

    @Test
    void rejectsAParallelismBelowOne() {
        assertThatThrownBy(() -> new ArmSchedule(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String awaitPeer(CountDownLatch bothRunning, String name) {
        bothRunning.countDown();
        try {
            assertThat(bothRunning.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return name;
    }
}
