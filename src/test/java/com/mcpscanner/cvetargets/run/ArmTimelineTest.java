package com.mcpscanner.cvetargets.run;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmTimelineTest {

    private final SteppingClock clock = new SteppingClock();
    private final ArmTimeline timeline = new ArmTimeline(clock);

    @Test
    void recordsEachPhaseDurationInLifecycleOrder() {
        timeline.timed(ArmPhase.TEARDOWN, () -> clock.advance(Duration.ofSeconds(3)));
        timeline.timed(ArmPhase.SLOT_WAIT, () -> {
            clock.advance(Duration.ofSeconds(1));
            return "slot";
        });
        timeline.timed(ArmPhase.AUDIT, () -> clock.advance(Duration.ofMillis(2500)));

        assertThat(timeline.phases()).containsExactly(
                org.assertj.core.api.Assertions.entry("slotWait", 1000L),
                org.assertj.core.api.Assertions.entry("audit", 2500L),
                org.assertj.core.api.Assertions.entry("teardown", 3000L));
    }

    @Test
    void recordsAPhaseThatFails() {
        assertThatThrownBy(() -> timeline.timed(ArmPhase.READINESS, () -> {
            clock.advance(Duration.ofSeconds(4));
            throw new IllegalStateException("never ready");
        })).hasMessage("never ready");

        assertThat(timeline.phases()).containsEntry("readiness", 4000L);
    }

    @Test
    void announcesEachPhaseAsItStarts() {
        java.util.List<ArmPhase> started = new java.util.ArrayList<>();
        ArmTimeline announcing = new ArmTimeline(clock, started::add);

        announcing.timed(ArmPhase.CONTAINERS_UP, () -> assertThat(started).containsExactly(ArmPhase.CONTAINERS_UP));
        announcing.timed(ArmPhase.AUDIT, () -> "audited");

        assertThat(started).containsExactly(ArmPhase.CONTAINERS_UP, ArmPhase.AUDIT);
    }

    static final class SteppingClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-09T12:00:00Z");

        void advance(Duration step) {
            now = now.plus(step);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
