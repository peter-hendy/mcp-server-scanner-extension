package com.mcpscanner.cvetargets.shared;

import com.mcpscanner.cvetargets.manifest.LoadedTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SharedTargetArms {

    private SharedTargetArms() {
    }

    public static List<SharedTargetArm> of(List<LoadedTarget> targets) {
        Map<BuildFingerprint, List<ManifestArm>> byBuild = new LinkedHashMap<>();
        targets.stream()
                .flatMap(target -> target.manifest().arms().stream().map(arm -> new ManifestArm(target, arm)))
                .forEach(member -> byBuild.computeIfAbsent(BuildFingerprint.of(member), build -> new ArrayList<>())
                        .add(member));
        return byBuild.values().stream().map(SharedTargetArm::new).toList();
    }
}
