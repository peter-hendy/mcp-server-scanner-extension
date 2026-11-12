package com.mcpscanner.ui.access;

@FunctionalInterface
public interface LaunchGate {

    LaunchGate DIRECT = Runnable::run;

    void run(Runnable action);
}
