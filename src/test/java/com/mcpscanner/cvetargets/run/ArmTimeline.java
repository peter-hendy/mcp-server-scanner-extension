package com.mcpscanner.cvetargets.run;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ArmTimeline {

    private final Clock clock;
    private final Consumer<ArmPhase> phaseStarted;
    private final Map<ArmPhase, Long> millis = new EnumMap<>(ArmPhase.class);

    ArmTimeline(Clock clock) {
        this(clock, phase -> { });
    }

    ArmTimeline(Clock clock, Consumer<ArmPhase> phaseStarted) {
        this.clock = clock;
        this.phaseStarted = phaseStarted;
    }

    <T> T timed(ArmPhase phase, Supplier<T> work) {
        phaseStarted.accept(phase);
        Instant started = clock.instant();
        try {
            return work.get();
        } finally {
            record(phase, Duration.between(started, clock.instant()).toMillis());
        }
    }

    void timed(ArmPhase phase, Runnable work) {
        timed(phase, () -> {
            work.run();
            return null;
        });
    }

    synchronized Map<String, Long> phases() {
        Map<String, Long> named = new LinkedHashMap<>();
        millis.forEach((phase, elapsed) -> named.put(phase.jsonName(), elapsed));
        return named;
    }

    private synchronized void record(ArmPhase phase, long elapsed) {
        millis.merge(phase, elapsed, Long::sum);
    }
}
