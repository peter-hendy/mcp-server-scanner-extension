package com.mcpscanner.cvetargets.run;

@FunctionalInterface
public interface ArmResourceMonitor {

    ArmResourceMonitor NONE = run -> () -> PeakUsage.NONE;

    WatchedArm watch(ArmRun run);

    @FunctionalInterface
    interface WatchedArm {
        PeakUsage stop();
    }
}
