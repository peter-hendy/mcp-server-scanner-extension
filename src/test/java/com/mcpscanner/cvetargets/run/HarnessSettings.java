package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.manifest.TargetSelection;

import java.nio.file.Path;
import java.util.Optional;

public record HarnessSettings(Path targetsRoot, Path resultsRoot, TargetSelection selection, int slots, Path slotsDirectory,
                              int parallelArms, boolean allChecks) {

    static final String TARGETS_ROOT_PROPERTY = "cveTargets.root";
    static final String RESULTS_ROOT_PROPERTY = "cveTargets.results";
    static final String CHECK_PROPERTY = "cveTargets.check";
    static final String TARGET_PROPERTY = "cveTargets.target";
    static final String SMOKE_PROPERTY = "cveTargets.smoke";
    static final String SLOTS_PROPERTY = "cveTargets.slots";
    static final String SLOTS_DIRECTORY_PROPERTY = "cveTargets.slotsDir";
    static final String PARALLEL_PROPERTY = "cveTargets.parallel";
    static final String ALL_CHECKS_PROPERTY = "cveTargets.allChecks";
    static final int DEFAULT_SLOTS = 2;

    public static HarnessSettings fromSystemProperties() {
        int slots = property(SLOTS_PROPERTY).map(value -> wholeNumber(value, "-PcveSlots")).orElse(DEFAULT_SLOTS);
        return new HarnessSettings(
                Path.of(System.getProperty(TARGETS_ROOT_PROPERTY, "cve-targets")).toAbsolutePath(),
                Path.of(System.getProperty(RESULTS_ROOT_PROPERTY, "build/cve-targets")).toAbsolutePath(),
                new TargetSelection(property(CHECK_PROPERTY), property(TARGET_PROPERTY), Boolean.getBoolean(SMOKE_PROPERTY)),
                slots,
                property(SLOTS_DIRECTORY_PROPERTY).map(Path::of)
                        .orElse(Path.of(System.getProperty("user.home"), ".cache", "mcp-cve-targets", "slots")),
                property(PARALLEL_PROPERTY).map(value -> wholeNumber(value, "-PcveParallel")).orElse(slots),
                Boolean.getBoolean(ALL_CHECKS_PROPERTY));
    }

    public Path recorderLocksDirectory() {
        return slotsDirectory.toAbsolutePath().getParent().resolve("recorder");
    }

    private static int wholeNumber(String value, String option) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(option + " must be a whole number, got " + value);
        }
    }

    private static Optional<String> property(String name) {
        return Optional.ofNullable(System.getProperty(name)).map(String::strip).filter(value -> !value.isEmpty());
    }
}
