package com.mcpscanner.access.mining;

public enum MiningStop {
    COMPLETED,
    CANCELLED,
    RATE_LIMITED,
    FAILED;

    public boolean completed() {
        return this == COMPLETED;
    }
}
