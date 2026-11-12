package com.mcpscanner.checks.shared.collaborator;

import java.time.Duration;

public final class ManualTicker implements WatchTicker {

    private Runnable tick = () -> { };
    private int starts;
    private boolean stopped;

    @Override
    public synchronized void start(Runnable tick, Duration interval) {
        this.tick = tick;
        starts++;
    }

    @Override
    public synchronized void stop() {
        stopped = true;
    }

    public void tick() {
        Runnable current;
        synchronized (this) {
            current = tick;
        }
        current.run();
    }

    public synchronized int starts() {
        return starts;
    }

    public synchronized boolean stopped() {
        return stopped;
    }
}
