package com.mcpscanner.ui.widgets;

public interface ReadScheduler {

    void schedule(Runnable task, long delayMillis);

    long nowMillis();

    void shutdown();
}
