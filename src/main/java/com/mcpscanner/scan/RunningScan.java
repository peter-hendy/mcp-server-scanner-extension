package com.mcpscanner.scan;

import java.util.function.Consumer;

public interface RunningScan {

    ScanActivity activity();

    boolean isFinished();

    boolean outlivesConnection();

    void cancel();

    void whenFinished(Consumer<ScanOutcome> listener);
}
