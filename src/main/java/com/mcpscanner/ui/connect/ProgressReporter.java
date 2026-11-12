package com.mcpscanner.ui.connect;

import com.mcpscanner.ui.state.ConnectPhase;

@FunctionalInterface
public interface ProgressReporter {

    void report(ConnectPhase phase, String message);
}
