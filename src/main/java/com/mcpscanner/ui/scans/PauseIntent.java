package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanStatus;

final class PauseIntent {

    private enum Requested { NOTHING, PAUSE, RESUME }

    private Requested requested = Requested.NOTHING;

    void settle(ScanStatus status) {
        if (!status.active() || requested == requestFor(paused(status))) {
            requested = Requested.NOTHING;
        }
    }

    boolean showsPaused(ScanStatus status) {
        return requested == Requested.NOTHING ? paused(status) : requested == Requested.PAUSE;
    }

    boolean toggle(ScanStatus status) {
        boolean pause = !showsPaused(status);
        requested = requestFor(pause);
        return pause;
    }

    private static Requested requestFor(boolean pause) {
        return pause ? Requested.PAUSE : Requested.RESUME;
    }

    private static boolean paused(ScanStatus status) {
        return status == ScanStatus.PAUSED;
    }
}
