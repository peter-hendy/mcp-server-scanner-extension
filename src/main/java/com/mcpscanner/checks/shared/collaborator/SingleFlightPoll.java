package com.mcpscanner.checks.shared.collaborator;

import java.time.Duration;
import java.util.function.LongSupplier;

final class SingleFlightPoll {

    private final LongSupplier nanoClock;
    private boolean inFlight;
    private boolean polled;
    private long lastPollNanos;

    SingleFlightPoll(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    synchronized boolean claimIfDue(Duration minimumInterval) {
        if (inFlight) {
            awaitInFlightPoll();
            return false;
        }
        if (polled && nanoClock.getAsLong() - lastPollNanos < minimumInterval.toNanos()) {
            return false;
        }
        inFlight = true;
        polled = true;
        lastPollNanos = nanoClock.getAsLong();
        return true;
    }

    synchronized void release() {
        inFlight = false;
        notifyAll();
    }

    private void awaitInFlightPoll() {
        while (inFlight) {
            try {
                wait();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
