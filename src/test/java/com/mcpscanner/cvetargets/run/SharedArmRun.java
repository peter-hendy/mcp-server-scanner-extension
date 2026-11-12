package com.mcpscanner.cvetargets.run;

import java.util.List;

public record SharedArmRun(ArmRun build, List<ArmRun> manifests) {

    public SharedArmRun {
        manifests = List.copyOf(manifests);
    }

    public static SharedArmRun alone(ArmRun run) {
        return new SharedArmRun(run, List.of(run));
    }
}
