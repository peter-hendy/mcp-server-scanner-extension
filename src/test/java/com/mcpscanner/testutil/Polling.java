package com.mcpscanner.testutil;

import org.awaitility.core.ConditionFactory;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

public final class Polling {

    private static final Duration IN_MEMORY_POLL_INTERVAL = Duration.ofMillis(10);

    private Polling() {
    }

    public static ConditionFactory within(Duration bound) {
        return await().atMost(bound).pollInterval(IN_MEMORY_POLL_INTERVAL);
    }
}
