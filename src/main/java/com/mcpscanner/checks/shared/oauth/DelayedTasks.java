package com.mcpscanner.checks.shared.oauth;

import java.time.Duration;

@FunctionalInterface
public interface DelayedTasks {

    void runAfter(Duration delay, Runnable task);
}
