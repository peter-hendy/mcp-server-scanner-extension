package com.mcpscanner.ui.widgets;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class DaemonReadScheduler implements ReadScheduler {

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "mcp-ui-reads");
        thread.setDaemon(true);
        return thread;
    }, new ScheduledThreadPoolExecutor.DiscardPolicy());

    @Override
    public void schedule(Runnable task, long delayMillis) {
        executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public long nowMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }
}
