package com.mcpscanner.engine;

import java.time.Instant;
import java.util.Optional;

final class ScanLifecycle {

    private Instant endedAt;
    private boolean cancelled;

    synchronized boolean cancel() {
        if (cancelled || endedAt != null) {
            return false;
        }
        cancelled = true;
        return true;
    }

    synchronized boolean end(Instant at) {
        if (endedAt != null) {
            return false;
        }
        endedAt = at;
        return true;
    }

    synchronized boolean cancelled() {
        return cancelled;
    }

    synchronized boolean ended() {
        return endedAt != null;
    }

    synchronized Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }

    synchronized ScanStatus status(boolean paused) {
        if (cancelled) {
            return ScanStatus.CANCELLED;
        }
        if (endedAt != null) {
            return ScanStatus.FINISHED;
        }
        return paused ? ScanStatus.PAUSED : ScanStatus.RUNNING;
    }
}
