package com.mcpscanner.checks.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlicedWaitTest {

    private final List<Duration> slices = new ArrayList<>();
    private final SlicedWait wait = new SlicedWait(slices::add);

    @Test
    void sleepsTheWholeWaitInSlicesOfAtMostOneSlice() throws InterruptedException {
        boolean waited = wait.waitedOut(Duration.ofMillis(1100), () -> true);

        assertThat(waited).isTrue();
        assertThat(slices).allSatisfy(slice -> assertThat(slice).isLessThanOrEqualTo(SlicedWait.SLICE));
        assertThat(slices.stream().reduce(Duration.ZERO, Duration::plus)).isEqualTo(Duration.ofMillis(1100));
    }

    @Test
    void givesUpAfterTheSliceInWhichItStoppedBeingWanted() throws InterruptedException {
        boolean waited = wait.waitedOut(Duration.ofSeconds(60), () -> slices.size() < 2);

        assertThat(waited).isFalse();
        assertThat(slices).hasSize(2);
    }

    @Test
    void doesNotSleepAWaitThatIsNoLongerWanted() throws InterruptedException {
        boolean waited = wait.waitedOut(Duration.ofSeconds(60), () -> false);

        assertThat(waited).isFalse();
        assertThat(slices).isEmpty();
    }

    @Test
    void aSliceIsAtMostOneSecond() {
        assertThat(SlicedWait.SLICE).isLessThanOrEqualTo(Duration.ofSeconds(1));
    }
}
