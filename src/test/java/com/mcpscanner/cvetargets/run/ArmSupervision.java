package com.mcpscanner.cvetargets.run;

import java.util.function.Consumer;

public record ArmSupervision(ArmWakeLock wakeLock, Consumer<String> progressLines) {

    public static final ArmSupervision NONE = new ArmSupervision(ArmWakeLock.NONE, line -> { });

    public static ArmSupervision onThisMachine() {
        return new ArmSupervision(ArmWakeLock.forThisMachine(), System.err::println);
    }
}
