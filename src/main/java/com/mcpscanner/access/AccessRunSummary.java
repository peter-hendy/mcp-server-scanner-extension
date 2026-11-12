package com.mcpscanner.access;

import java.util.Optional;

public record AccessRunSummary(int checked, int total, int issues, boolean cancelled, Optional<String> stopReason) {

    public AccessRunSummary {
        stopReason = stopReason == null ? Optional.empty() : stopReason;
    }

    public AccessRunSummary(int checked, int total, int issues, boolean cancelled) {
        this(checked, total, issues, cancelled, Optional.empty());
    }
}
