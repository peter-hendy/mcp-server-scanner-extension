package com.mcpscanner.checks.shared.collaborator;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class ScheduledWatchTicker implements WatchTicker {

    private static final String THREAD_NAME = "mcp-collaborator-watch";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void start(Runnable tick, Duration interval) {
        long millis = Math.max(1, interval.toMillis());
        executor.scheduleWithFixedDelay(tick, millis, millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        executor.shutdownNow();
    }
}
