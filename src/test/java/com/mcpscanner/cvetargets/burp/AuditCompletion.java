package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.AuditSnapshot;
import com.mcpscanner.cvetargets.environment.Pause;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class AuditCompletion {

    static final String FINISHED = "finished";

    private final Pause pause;
    private final Clock clock;
    private final Duration pollInterval;
    private final Duration quietPeriod;

    AuditCompletion(Pause pause, Clock clock, Duration pollInterval, Duration quietPeriod) {
        this.pause = pause;
        this.clock = clock;
        this.pollInterval = pollInterval;
        this.quietPeriod = quietPeriod;
    }

    CompletedAudit await(BurpAutomation burp, BooleanSupplier tornDown) {
        return await(burp, tornDown, snapshot -> { });
    }

    CompletedAudit await(BurpAutomation burp, BooleanSupplier tornDown, Consumer<AuditSnapshot> polled) {
        AuditSnapshot last = current(burp);
        polled.accept(last);
        Instant lastProgress = clock.instant();
        while (!FINISHED.equalsIgnoreCase(last.status())) {
            if (tornDown.getAsBoolean()) {
                throw new IllegalStateException("the arm was torn down while Burp was still auditing ("
                        + last.status() + ", " + last.requestCount() + " requests)");
            }
            pause.pause(pollInterval);
            AuditSnapshot next = current(burp);
            polled.accept(next);
            if (next.requestCount() != last.requestCount()) {
                lastProgress = clock.instant();
            } else if (Duration.between(lastProgress, clock.instant()).compareTo(quietPeriod) >= 0) {
                return new CompletedAudit(next, CompletedAudit.AuditEnd.QUIESCENT);
            }
            last = next;
        }
        return new CompletedAudit(last, CompletedAudit.AuditEnd.FINISHED);
    }

    private static AuditSnapshot current(BurpAutomation burp) {
        return burp.audit().orElseThrow(() -> new IllegalStateException("Burp reports no launched audit"));
    }
}
