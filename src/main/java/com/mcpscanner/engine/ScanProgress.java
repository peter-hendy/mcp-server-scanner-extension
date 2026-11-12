package com.mcpscanner.engine;

public record ScanProgress(int planned, int completed, int skipped, int issuesReported, boolean cancelled,
                           int watchedPayloads) {

    public ScanProgress(int planned, int completed, int skipped, int issuesReported, boolean cancelled) {
        this(planned, completed, skipped, issuesReported, cancelled, 0);
    }
}
