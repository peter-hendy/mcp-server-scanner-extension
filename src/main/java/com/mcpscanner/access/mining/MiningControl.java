package com.mcpscanner.access.mining;

import java.util.function.BooleanSupplier;

public record MiningControl(MiningProgress progress, BooleanSupplier cancelled, WriteCallApproval writes) {

    public MiningControl(MiningProgress progress, BooleanSupplier cancelled) {
        this(progress, cancelled, WriteCallApproval.REFUSE);
    }

    public static MiningControl unattended() {
        return new MiningControl(MiningProgress.NONE, () -> false);
    }
}
