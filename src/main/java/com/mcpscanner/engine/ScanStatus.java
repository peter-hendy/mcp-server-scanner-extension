package com.mcpscanner.engine;

public enum ScanStatus {
    RUNNING,
    PAUSED,
    FINISHED,
    CANCELLED;

    public boolean active() {
        return this == RUNNING || this == PAUSED;
    }
}
