package com.mcpscanner.ui.scans;

public interface ScanControls {

    void pause(long scanId);

    void resume(long scanId);

    void cancel(long scanId);

    void remove(long scanId);

    void clearFinished();
}
