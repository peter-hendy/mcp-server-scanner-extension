package com.mcpscanner.access.mining;

import java.util.List;

public record MiningReport(MiningStop stop, List<String> warnings, int privateValues, int sharedValues) {

    public MiningReport {
        warnings = List.copyOf(warnings);
    }

    static MiningReport failed(String reason) {
        return new MiningReport(MiningStop.FAILED, List.of("collecting failed: " + reason), 0, 0);
    }

    public String summary() {
        return switch (stop) {
            case CANCELLED -> "Collecting cancelled; nothing was saved";
            case RATE_LIMITED -> "Collecting stopped: the server rate-limited the requests; nothing was saved";
            case FAILED -> "Collecting failed; nothing was saved";
            case COMPLETED -> "Collected " + found();
        };
    }

    private String found() {
        return privateValues + " private and " + sharedValues + " shared " + (privateValues + sharedValues == 1
                ? "ID" : "IDs");
    }
}
