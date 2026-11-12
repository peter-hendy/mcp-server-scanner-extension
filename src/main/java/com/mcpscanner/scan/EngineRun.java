package com.mcpscanner.scan;

import com.mcpscanner.engine.EngineScan;
import com.mcpscanner.engine.ScanProgress;

import java.util.function.Consumer;

record EngineRun(EngineScan scan) implements RunningScan {

    @Override
    public ScanActivity activity() {
        ScanProgress progress = scan.snapshot().progress();
        return ScanActivity.mcpOnlyScan(progress.completed(), progress.issuesReported());
    }

    @Override
    public boolean isFinished() {
        return scan.completion().isDone();
    }

    @Override
    public boolean outlivesConnection() {
        return false;
    }

    @Override
    public void cancel() {
        scan.cancel();
    }

    @Override
    public void whenFinished(Consumer<ScanOutcome> listener) {
        scan.completion().thenAccept(progress ->
                listener.accept(new ScanOutcome(progress.issuesReported(), progress.cancelled(),
                        progress.watchedPayloads())));
    }
}
