package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.CheckAccessPhase;
import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.testutil.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckAccessCompletionTest {

    private static final Duration POLL = Duration.ofSeconds(5);
    private static final Duration LIMIT = Duration.ofMinutes(1);
    private static final Instant START = Instant.parse("2026-11-11T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final CheckAccessCompletion completion = new CheckAccessCompletion(clock::advance, clock, POLL, LIMIT);

    @Test
    void pollsUntilCheckAccessEndsAndShowsEveryPollAsProgress() {
        List<CheckAccessPhase> polled = new ArrayList<>();

        CheckAccessStatus ended = completion.await(scripted(status(CheckAccessPhase.MINING),
                status(CheckAccessPhase.RUNNING), status(CheckAccessPhase.FINISHED)), () -> false,
                status -> polled.add(status.status()));

        assertThat(ended.status()).isEqualTo(CheckAccessPhase.FINISHED);
        assertThat(polled).containsExactly(CheckAccessPhase.MINING, CheckAccessPhase.RUNNING,
                CheckAccessPhase.FINISHED);
        assertThat(Duration.between(START, clock.instant())).isEqualTo(POLL.multipliedBy(2));
    }

    @Test
    void returnsAFailedLaunchAsItsEndForTheCallerToJudge() {
        CheckAccessStatus ended = completion.await(scripted(status(CheckAccessPhase.FAILED)), () -> false,
                status -> { });

        assertThat(ended.status()).isEqualTo(CheckAccessPhase.FAILED);
    }

    @Test
    void givesUpOnceTheLimitPassesWithoutAnEnd() {
        assertThatThrownBy(() -> completion.await(scripted(status(CheckAccessPhase.RUNNING)), () -> false,
                status -> { }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not end within");
        assertThat(Duration.between(START, clock.instant())).isGreaterThanOrEqualTo(LIMIT);
    }

    @Test
    void stopsWaitingWhenTheArmIsTornDown() {
        assertThatThrownBy(() -> completion.await(scripted(status(CheckAccessPhase.RUNNING)), () -> true,
                status -> { }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("torn down");
    }

    @Test
    void failsWhenBurpReportsNoLaunchedCheckAccess() {
        assertThatThrownBy(() -> completion.await(new FakeBurpAutomation(Optional::empty), () -> false,
                status -> { }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no launched Check access");
    }

    private static CheckAccessStatus status(CheckAccessPhase phase) {
        return new CheckAccessStatus(phase, 0, 0, 0, 0, List.of(), null);
    }

    private static BurpAutomation scripted(CheckAccessStatus... statuses) {
        Deque<CheckAccessStatus> remaining = new ArrayDeque<>(List.of(statuses));
        return new FakeBurpAutomation(() -> Optional.of(remaining.size() > 1 ? remaining.poll() : remaining.peek()));
    }
}
