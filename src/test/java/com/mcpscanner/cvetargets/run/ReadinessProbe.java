package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.requests.ArmEndpoint;

import java.time.Duration;

@FunctionalInterface
public interface ReadinessProbe {
    void awaitReady(ArmEndpoint endpoint, Duration timeout);
}
