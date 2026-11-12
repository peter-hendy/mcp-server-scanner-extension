package com.mcpscanner.cvetargets.shared;

import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetArm;

public record ManifestArm(LoadedTarget target, TargetArm arm) {
}
