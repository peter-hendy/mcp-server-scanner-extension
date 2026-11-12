package com.mcpscanner.ui.scans;

import com.mcpscanner.ui.widgets.UiReads;

record OffEdtControls(ScanControls controls, UiReads reads) implements ScanControls {

    @Override
    public void pause(long scanId) {
        reads.run(() -> controls.pause(scanId));
    }

    @Override
    public void resume(long scanId) {
        reads.run(() -> controls.resume(scanId));
    }

    @Override
    public void cancel(long scanId) {
        reads.run(() -> controls.cancel(scanId));
    }

    @Override
    public void remove(long scanId) {
        reads.run(() -> controls.remove(scanId));
    }

    @Override
    public void clearFinished() {
        reads.run(controls::clearFinished);
    }
}
