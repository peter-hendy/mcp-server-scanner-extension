package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.cvetargets.environment.Pause;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class CheckAccessCompletion {

    private final Pause pause;
    private final Clock clock;
    private final Duration pollInterval;
    private final Duration limit;

    CheckAccessCompletion(Pause pause, Clock clock, Duration pollInterval, Duration limit) {
        this.pause = pause;
        this.clock = clock;
        this.pollInterval = pollInterval;
        this.limit = limit;
    }

    CheckAccessStatus await(BurpAutomation burp, BooleanSupplier tornDown, Consumer<CheckAccessStatus> polled) {
        Instant deadline = clock.instant().plus(limit);
        CheckAccessStatus last = current(burp);
        polled.accept(last);
        while (!last.status().ended()) {
            requireStillWaiting(last, tornDown, deadline);
            pause.pause(pollInterval);
            last = current(burp);
            polled.accept(last);
        }
        return last;
    }

    private void requireStillWaiting(CheckAccessStatus last, BooleanSupplier tornDown, Instant deadline) {
        if (tornDown.getAsBoolean()) {
            throw new IllegalStateException("the arm was torn down while Check access was " + describe(last));
        }
        if (!clock.instant().isBefore(deadline)) {
            throw new IllegalStateException("Check access in Burp did not end within " + limit + " (" + describe(last)
                    + ")");
        }
    }

    private static String describe(CheckAccessStatus status) {
        return status.status().name().toLowerCase(java.util.Locale.ROOT) + ", " + status.checked() + " of "
                + status.total() + " items";
    }

    private static CheckAccessStatus current(BurpAutomation burp) {
        return burp.checkAccessStatus()
                .orElseThrow(() -> new IllegalStateException("Burp reports no launched Check access"));
    }
}
