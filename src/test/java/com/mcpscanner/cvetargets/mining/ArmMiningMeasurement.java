package com.mcpscanner.cvetargets.mining;

import java.util.List;

public record ArmMiningMeasurement(List<IdentityMeasurement> identities, String stop, List<String> runWarnings,
                                   long miningMillis, int tools, int readOnlyTools, int resources, int templates,
                                   int prompts, List<RecordingMiningSender.SentCall> calls) {
}
