package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.LaunchedScan;
import com.mcpscanner.automation.ScanAllCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class RetriedScanAll {

    static final int ATTEMPTS = 3;

    private RetriedScanAll() {
    }

    static LaunchedScan launch(BurpAutomation burp, ScanAllCommand command) {
        return retried("Scan all", () -> burp.scanAll(command));
    }

    static <T> T retried(String operation, Supplier<T> launch) {
        List<String> failures = new ArrayList<>();
        while (failures.size() < ATTEMPTS) {
            try {
                return launch.get();
            } catch (IllegalStateException failed) {
                failures.add(failed.getMessage());
            }
        }
        throw new IllegalStateException(operation + " failed " + ATTEMPTS + " times: " + String.join(" ; ", failures));
    }
}
