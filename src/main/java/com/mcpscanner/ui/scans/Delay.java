package com.mcpscanner.ui.scans;

@FunctionalInterface
public interface Delay {

    Delay IMMEDIATE = (millis, task) -> task.run();

    void after(long millis, Runnable task);
}
