package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.requests.ArmEndpoint;

import java.util.Optional;

@FunctionalInterface
public interface ReadinessAttempt {

    Optional<String> notReadyBecause(ArmEndpoint endpoint);
}
