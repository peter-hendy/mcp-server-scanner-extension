package com.mcpscanner.cvetargets.mining;

import com.mcpscanner.cvetargets.run.ArmResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class MiningMeasurementReport {

    private static final String HEADER = "| Target | Arm | Identity | Owned objects | Found | Private | #private | #shared "
            + "| Requests | Warnings | Mining s | Arm s |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n";

    private final Path file;

    public MiningMeasurementReport(Path file) {
        this.file = file;
    }

    public void write(List<ArmResult> results) {
        StringBuilder text = new StringBuilder("# Access mining measurement\n\n").append(HEADER);
        results.forEach(result -> text.append(rows(result)));
        text.append("\n## Arms\n\n| Target | Arm | Stop | Run warnings | Tools (read-only) | Resources | Templates "
                + "| Prompts | Error |\n|---|---|---|---|---|---|---|---|---|\n");
        results.forEach(result -> text.append(armRow(result)));
        text.append("\n## Private values sampled\n\n");
        results.forEach(result -> text.append(samples(result)));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text.toString());
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private static String rows(ArmResult result) {
        if (!(result.auditDetails() instanceof ArmMiningMeasurement measurement)) {
            return "| " + result.target() + " | " + result.arm() + " | - | - | - | - | - | - | - | "
                    + escaped(String.valueOf(result.error())) + " | - | " + seconds(result.durationMillis()) + " |\n";
        }
        return measurement.identities().stream().map(identity -> "| " + result.target() + " | " + result.arm() + " | "
                + identity.name() + " | " + owned(identity) + " | " + found(identity, false) + " | "
                + found(identity, true) + " | " + identity.privateCount() + " | " + identity.sharedCount() + " | "
                + identity.requestsSent() + " | " + String.join(",", identity.warnings()) + " | "
                + seconds(measurement.miningMillis()) + " | " + seconds(result.durationMillis()) + " |\n")
                .collect(Collectors.joining());
    }

    private static String armRow(ArmResult result) {
        if (!(result.auditDetails() instanceof ArmMiningMeasurement measurement)) {
            return "| " + result.target() + " | " + result.arm() + " | - | - | - | - | - | - | "
                    + escaped(String.valueOf(result.error())) + " |\n";
        }
        return "| " + result.target() + " | " + result.arm() + " | " + measurement.stop() + " | "
                + String.join(",", measurement.runWarnings()) + " | " + measurement.tools() + " ("
                + measurement.readOnlyTools() + ") | " + measurement.resources() + " | " + measurement.templates()
                + " | " + measurement.prompts() + " | " + escaped(String.valueOf(result.error())) + " |\n";
    }

    private static String samples(ArmResult result) {
        if (!(result.auditDetails() instanceof ArmMiningMeasurement measurement)) {
            return "";
        }
        return measurement.identities().stream()
                .filter(identity -> !identity.privateValues().isEmpty())
                .map(identity -> "- " + result.target() + "/" + result.arm() + " " + identity.name() + ": "
                        + escaped(String.join(", ", identity.privateValues())) + "\n")
                .collect(Collectors.joining());
    }

    private static String owned(IdentityMeasurement identity) {
        return identity.ownedObjects().isEmpty() ? "none declared" : identity.ownedObjects().stream()
                .map(owned -> owned.argument() + "=" + owned.value()).collect(Collectors.joining(", "));
    }

    private static String found(IdentityMeasurement identity, boolean privately) {
        if (identity.ownedObjects().isEmpty()) {
            return "-";
        }
        long hits = identity.ownedObjects().stream()
                .filter(owned -> privately ? owned.classifiedPrivate() : owned.found()).count();
        return hits + "/" + identity.ownedObjects().size();
    }

    private static String seconds(long millis) {
        return String.valueOf(millis / 1000);
    }

    private static String escaped(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
