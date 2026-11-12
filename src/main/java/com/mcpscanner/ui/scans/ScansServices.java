package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanHistory;
import com.mcpscanner.ui.widgets.UiReads;

public record ScansServices(ScanFeed feed, ScanControls controls, TargetNavigator navigator,
                            MessageEditors editors) {

    public static ScansServices of(ScanHistory history, TargetNavigator navigator, MessageEditors editors) {
        HistoryScans scans = new HistoryScans(history);
        return new ScansServices(scans, scans, navigator, editors);
    }

    CardServices forCards(UiReads reads) {
        return new CardServices(new OffEdtControls(controls, reads), navigator, editors);
    }
}
