package com.mcpscanner.access;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AccessRun {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean cancelled() {
        return cancelled.get();
    }
}
