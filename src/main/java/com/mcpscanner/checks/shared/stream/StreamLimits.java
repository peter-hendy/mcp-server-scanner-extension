package com.mcpscanner.checks.shared.stream;

import java.time.Duration;

public record StreamLimits(Duration headerTimeout, Duration holdWindow, int maxPrefixBytes) {

    public static StreamLimits standard() {
        return new StreamLimits(Duration.ofSeconds(10), Duration.ofSeconds(1), 4096);
    }
}
