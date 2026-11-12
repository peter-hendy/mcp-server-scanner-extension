package com.mcpscanner.access.mining;

@FunctionalInterface
public interface MiningProgress {

    MiningProgress NONE = step -> {
    };

    void progressed(MiningStep step);
}
