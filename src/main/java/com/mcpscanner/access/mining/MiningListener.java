package com.mcpscanner.access.mining;

import java.util.function.Consumer;

public interface MiningListener {

    MiningListener NONE = onFinished(report -> {
    });

    void progressed(String user, MiningStep step);

    void finished(MiningReport report);

    static MiningListener onFinished(Consumer<MiningReport> finished) {
        return new MiningListener() {
            @Override
            public void progressed(String user, MiningStep step) {
            }

            @Override
            public void finished(MiningReport report) {
                finished.accept(report);
            }
        };
    }
}
