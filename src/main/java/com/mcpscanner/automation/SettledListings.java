package com.mcpscanner.automation;

import com.mcpscanner.identity.discovery.DiscoveryOutcome;
import com.mcpscanner.identity.discovery.IdentityListings;

import java.time.Duration;

public final class SettledListings {

    private final IdentityListings listings;
    private final Duration timeout;
    private final Object changed = new Object();

    public SettledListings(IdentityListings listings, Duration timeout) {
        this.listings = listings;
        this.timeout = timeout;
    }

    public void await() {
        Runnable signal = this::signalChange;
        listings.addListener(signal);
        try {
            awaitNoneDiscovering(System.nanoTime() + timeout.toNanos());
        } finally {
            listings.removeListener(signal);
        }
    }

    private void awaitNoneDiscovering(long deadline) {
        synchronized (changed) {
            while (anyDiscovering()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException("Identity discovery did not finish within " + timeout);
                }
                waitForChange(remaining);
            }
        }
    }

    private void waitForChange(long nanos) {
        try {
            Duration remaining = Duration.ofNanos(nanos);
            changed.wait(Math.max(1, remaining.toMillis()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for identity discovery", interrupted);
        }
    }

    private boolean anyDiscovering() {
        return listings.outcomes().values().stream().anyMatch(DiscoveryOutcome.Discovering.class::isInstance);
    }

    private void signalChange() {
        synchronized (changed) {
            changed.notifyAll();
        }
    }
}
