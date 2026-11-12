package com.mcpscanner.checks.catalog.commandinjection;

import java.util.Optional;

record ProbeOutcome(Optional<Finding> finding, boolean conclusive) {

    static ProbeOutcome found(Finding finding) {
        return new ProbeOutcome(Optional.of(finding), true);
    }

    static ProbeOutcome inconclusive() {
        return new ProbeOutcome(Optional.empty(), false);
    }
}
