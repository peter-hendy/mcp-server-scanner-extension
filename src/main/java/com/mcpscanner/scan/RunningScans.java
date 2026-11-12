package com.mcpscanner.scan;

import com.mcpscanner.logging.McpEventLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RunningScans {

    private final McpEventLog eventLog;
    private final CopyOnWriteArrayList<RunningScan> scans = new CopyOnWriteArrayList<>();

    public RunningScans(McpEventLog eventLog) {
        this.eventLog = eventLog;
    }

    <T extends RunningScan> T track(T scan) {
        scans.add(scan);
        return scan;
    }

    ScanActivity activity() {
        scans.removeIf(RunningScan::isFinished);
        return scans.stream().map(RunningScan::activity).reduce(ScanActivity.idle(), ScanActivity::plus);
    }

    void cancelAll() {
        untrackAll().forEach(this::tryCancel);
    }

    void clear() {
        untrackAll().stream().filter(scan -> !scan.outlivesConnection()).forEach(this::tryCancel);
    }

    private List<RunningScan> untrackAll() {
        List<RunningScan> untracked = List.copyOf(scans);
        scans.removeAll(untracked);
        return untracked;
    }

    private void tryCancel(RunningScan scan) {
        try {
            scan.cancel();
        } catch (RuntimeException e) {
            eventLog.warn("Scan cancel failed: " + e.getClass().getSimpleName());
        }
    }
}
