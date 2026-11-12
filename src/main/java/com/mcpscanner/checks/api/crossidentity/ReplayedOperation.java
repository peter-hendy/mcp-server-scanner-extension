package com.mcpscanner.checks.api.crossidentity;

import com.mcpscanner.checks.api.ReplayPermission;

public enum ReplayedOperation {
    READ("."),
    WRITE(", so a state-changing operation was repeated.");

    private static final String PROBE_CONTRACT = "Probe contract: the captured operation was replayed as supplied "
            + "for every arm";

    private final String consequence;

    ReplayedOperation(String consequence) {
        this.consequence = consequence;
    }

    public static ReplayedOperation of(OperationSeed seed, ReplayPermission permission) {
        return permission.replaysWrite(seed.item()) ? WRITE : READ;
    }

    String probeContract() {
        return PROBE_CONTRACT + consequence;
    }
}
