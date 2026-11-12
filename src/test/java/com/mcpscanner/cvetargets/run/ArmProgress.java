package com.mcpscanner.cvetargets.run;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ArmProgress {

    private final AtomicReference<ArmObservation> latest = new AtomicReference<>();
    private final ArmProgressReport report;

    public ArmProgress() {
        this(ArmProgressReport.SILENT);
    }

    public ArmProgress(ArmProgressReport report) {
        this.report = report;
    }

    public void record(ArmObservation observation) {
        latest.set(observation);
        report.observed(observation);
    }

    public void note(String message) {
        report.note(message);
    }

    public Optional<ArmObservation> latest() {
        return Optional.ofNullable(latest.get());
    }
}
