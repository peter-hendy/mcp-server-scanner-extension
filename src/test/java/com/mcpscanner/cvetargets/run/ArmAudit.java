package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.requests.ArmEndpoint;

import java.time.Duration;
import java.util.Optional;

public interface ArmAudit {

    String mode();

    ArmObservation audit(ArmRun run, ArmEndpoint endpoint, ArmTeardown teardown, ArmProgress progress);

    default Optional<String> notRunnable(ArmRun run) {
        return Optional.empty();
    }

    default Duration armTimeout(Duration declared) {
        return declared;
    }
}
