package com.mcpscanner.cvetargets.run;

public enum ArmPhase {
    SLOT_WAIT("slotWait"),
    CONTAINERS_UP("containersUp"),
    READINESS("readiness"),
    AUDIT("audit"),
    TEARDOWN("teardown");

    private final String jsonName;

    ArmPhase(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }
}
