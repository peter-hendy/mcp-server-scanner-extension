package com.mcpscanner.scan;

public record ScanOutcome(int issues, boolean cancelled, int watchedPayloads) {

    public ScanOutcome(int issues, boolean cancelled) {
        this(issues, cancelled, 0);
    }
}
