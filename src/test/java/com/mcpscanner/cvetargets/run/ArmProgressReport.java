package com.mcpscanner.cvetargets.run;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public final class ArmProgressReport {

    static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(60);
    static final ArmProgressReport SILENT = new ArmProgressReport("", Clock.systemUTC(), DEFAULT_INTERVAL, line -> { });

    private final String arm;
    private final Clock clock;
    private final Duration interval;
    private final Consumer<String> sink;
    private final Instant started;
    private Instant lastObservedLine;

    public ArmProgressReport(String arm, Clock clock, Duration interval, Consumer<String> sink) {
        this.arm = arm;
        this.clock = clock;
        this.interval = interval;
        this.sink = sink;
        this.started = clock.instant();
    }

    void phaseStarted(ArmPhase phase) {
        sink.accept(prefix(phase.jsonName()));
    }

    void note(String message) {
        sink.accept("cve-targets: " + arm + ": " + message);
    }

    synchronized void observed(ArmObservation observation) {
        Instant now = clock.instant();
        if (lastObservedLine != null && Duration.between(lastObservedLine, now).compareTo(interval) < 0) {
            return;
        }
        lastObservedLine = now;
        sink.accept(prefix("auditing") + ": " + summary(observation) + detail(observation));
    }

    private String prefix(String activity) {
        return "cve-targets: " + arm + ": " + activity + " (" + elapsed() + " in)";
    }

    private String elapsed() {
        Duration elapsed = Duration.between(started, clock.instant());
        return elapsed.toMinutes() > 0
                ? elapsed.toMinutes() + "m " + elapsed.toSecondsPart() + "s"
                : elapsed.toSeconds() + "s";
    }

    private static String summary(ArmObservation observation) {
        return observation.baseRequestCount() + " base requests, " + observation.probes().sent() + " probes sent, "
                + observation.probes().reached() + " reached, " + observation.issues().size() + " issues";
    }

    private static String detail(ArmObservation observation) {
        return observation.auditDetails() instanceof ArmProgressDetail detail ? "; " + detail.progress() : "";
    }
}
