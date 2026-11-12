package com.mcpscanner.checks.api;

import java.util.function.BooleanSupplier;

public final class RetryAfterWaits {

    private final BooleanSupplier onWaitingThread;
    private final BooleanSupplier stillWanted;

    private RetryAfterWaits(BooleanSupplier onWaitingThread, BooleanSupplier stillWanted) {
        this.onWaitingThread = onWaitingThread;
        this.stillWanted = stillWanted;
    }

    public static RetryAfterWaits onAnyThread(BooleanSupplier stillWanted) {
        return new RetryAfterWaits(() -> true, stillWanted);
    }

    public static RetryAfterWaits onThisThreadOnly(BooleanSupplier stillWanted) {
        Thread owner = Thread.currentThread();
        return new RetryAfterWaits(() -> Thread.currentThread() == owner, stillWanted);
    }

    boolean onWaitingThread() {
        return onWaitingThread.getAsBoolean();
    }

    boolean stillWanted() {
        return stillWanted.getAsBoolean();
    }
}
