package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetArm;

@FunctionalInterface
public interface TargetEnvironment {
    RunningArm start(LoadedTarget target, TargetArm arm);

    default ReservedArm reserve(LoadedTarget target, TargetArm arm) {
        return new ReservedArm(() -> start(target, arm), () -> { });
    }
}
