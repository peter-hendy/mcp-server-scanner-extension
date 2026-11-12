package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanHistory;
import com.mcpscanner.engine.ScanSnapshot;

import java.util.List;

record HistoryScans(ScanHistory history) implements ScanFeed, ScanControls {

    @Override
    public List<ScanSnapshot> snapshots() {
        return history.snapshots();
    }

    @Override
    public void addListener(Runnable listener) {
        history.addListener(listener);
    }

    @Override
    public void removeListener(Runnable listener) {
        history.removeListener(listener);
    }

    @Override
    public void pause(long scanId) {
        history.pause(scanId);
    }

    @Override
    public void resume(long scanId) {
        history.resume(scanId);
    }

    @Override
    public void cancel(long scanId) {
        history.cancel(scanId);
    }

    @Override
    public void remove(long scanId) {
        history.remove(scanId);
    }

    @Override
    public void clearFinished() {
        history.clearFinished();
    }
}
