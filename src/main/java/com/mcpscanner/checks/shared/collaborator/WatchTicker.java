package com.mcpscanner.checks.shared.collaborator;

import java.time.Duration;

public interface WatchTicker {

    void start(Runnable tick, Duration interval);

    void stop();

    static WatchTicker scheduled() {
        return new ScheduledWatchTicker();
    }
}
