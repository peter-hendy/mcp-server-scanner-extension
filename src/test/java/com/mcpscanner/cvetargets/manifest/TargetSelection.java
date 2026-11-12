package com.mcpscanner.cvetargets.manifest;

import java.util.Arrays;
import java.util.Optional;

public record TargetSelection(Optional<String> check, Optional<String> target, boolean smokeOnly) {

    public TargetSelection(Optional<String> check, Optional<String> target) {
        this(check, target, false);
    }

    public static TargetSelection everything() {
        return new TargetSelection(Optional.empty(), Optional.empty());
    }

    public static TargetSelection smokeSet() {
        return new TargetSelection(Optional.empty(), Optional.empty(), true);
    }

    public boolean includes(TargetManifest manifest) {
        return named(check, manifest.check()) && named(target, manifest.id()) && (!smokeOnly || manifest.smoke());
    }

    private static boolean named(Optional<String> names, String candidate) {
        return names.map(list -> Arrays.stream(list.split(",")).map(String::strip).anyMatch(candidate::equals))
                .orElse(true);
    }
}
