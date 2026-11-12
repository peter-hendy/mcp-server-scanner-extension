package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanSnapshot;

import java.util.List;

public interface ScanFeed {

    List<ScanSnapshot> snapshots();

    void addListener(Runnable listener);

    void removeListener(Runnable listener);
}
