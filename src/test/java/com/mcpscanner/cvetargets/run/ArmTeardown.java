package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.environment.RunningArm;

public final class ArmTeardown implements AutoCloseable {

    private RunningArm arm;
    private boolean closed;

    synchronized RunningArm hold(RunningArm started) {
        if (closed) {
            started.close();
            throw new IllegalStateException("the arm started after its run was torn down");
        }
        arm = started;
        return started;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        RunningArm started;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            started = arm;
        }
        if (started != null) {
            closeUninterrupted(started);
        }
    }

    private static void closeUninterrupted(RunningArm started) {
        boolean interrupted = Thread.interrupted();
        try {
            started.close();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
