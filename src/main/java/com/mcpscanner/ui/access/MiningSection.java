package com.mcpscanner.ui.access;

import com.mcpscanner.access.mining.WriteCallApproval;

final class MiningSection {

    private final MineIdsControl control;
    private final OwnedValuesView values;
    private final MineFirstGate gate;

    MiningSection(MiningServices services, WriteCallApproval approval, java.awt.Component parent) {
        MiningConsent consent = new MiningConsent(services.mining(), services.dialogs());
        this.control = new MineIdsControl(services, consent, approval);
        this.values = new OwnedValuesView(services.ownedValues(), services.users(), services.dialogs());
        this.gate = new MineFirstGate(services.mining(), consent, parent, control);
    }

    MineIdsControl control() {
        return control;
    }

    OwnedValuesView values() {
        return values;
    }

    LaunchGate mineFirst() {
        return gate;
    }

    void refresh() {
        control.refresh();
        values.refresh();
    }

    void dispose() {
        control.dispose();
        values.dispose();
    }
}
