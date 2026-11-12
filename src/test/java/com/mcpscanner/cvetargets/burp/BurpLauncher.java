package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.cvetargets.run.ArmRun;

import java.util.function.BooleanSupplier;

@FunctionalInterface
public interface BurpLauncher {

    BurpSession start(String label, String armOrigin, BooleanSupplier abandoned);

    static String labelFor(ArmRun run) {
        return run.checkId() + "-" + run.target().manifest().id() + "-" + run.arm().name();
    }
}
