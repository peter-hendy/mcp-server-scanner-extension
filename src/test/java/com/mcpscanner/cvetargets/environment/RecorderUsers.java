package com.mcpscanner.cvetargets.environment;

import java.util.function.Consumer;

@FunctionalInterface
public interface RecorderUsers {

    void setUp(Consumer<RecorderOccupancy> setup);
}
