package com.mcpscanner.access.mining;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MiningRun {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    boolean cancelled() {
        return cancelled.get();
    }
}
