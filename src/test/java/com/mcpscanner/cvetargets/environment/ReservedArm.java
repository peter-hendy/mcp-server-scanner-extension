package com.mcpscanner.cvetargets.environment;

import java.util.function.Supplier;

public final class ReservedArm implements AutoCloseable {

    private final Supplier<RunningArm> starter;
    private final Runnable release;
    private boolean claimed;

    public ReservedArm(Supplier<RunningArm> starter, Runnable release) {
        this.starter = starter;
        this.release = release;
    }

    public RunningArm start() {
        if (!claim()) {
            throw new IllegalStateException("a reserved arm starts at most once and never after its reservation closed");
        }
        return starter.get();
    }

    @Override
    public void close() {
        if (claim()) {
            release.run();
        }
    }

    private synchronized boolean claim() {
        if (claimed) {
            return false;
        }
        claimed = true;
        return true;
    }
}
