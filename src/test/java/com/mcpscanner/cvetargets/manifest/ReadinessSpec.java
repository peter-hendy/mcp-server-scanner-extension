package com.mcpscanner.cvetargets.manifest;

import java.time.Duration;
import java.util.Optional;

public record ReadinessSpec(ReadinessProbeType probe, int timeoutSeconds, SeedRequest call) {

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    public ReadinessSpec {
        probe = probe == null ? ReadinessProbeType.MCP_INITIALIZE : probe;
        timeoutSeconds = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
    }

    public static ReadinessSpec defaults() {
        return new ReadinessSpec(null, 0, null);
    }

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    public Optional<SeedRequest> authenticatedCall() {
        return Optional.ofNullable(call);
    }
}
