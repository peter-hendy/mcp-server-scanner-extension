package com.mcpscanner.ui.access;

import com.mcpscanner.access.mining.AccessMining;
import com.mcpscanner.access.mining.MiningBlocker;

import java.awt.Component;
import java.util.Optional;

final class MineFirstGate implements LaunchGate {

    private final AccessMining mining;
    private final MiningConsent consent;
    private final Component parent;
    private final MineIdsControl control;

    MineFirstGate(AccessMining mining, MiningConsent consent, Component parent, MineIdsControl control) {
        this.mining = mining;
        this.consent = consent;
        this.parent = parent;
        this.control = control;
    }

    @Override
    public void run(Runnable action) {
        Optional<MiningBlocker> blocker = mining.blocker();
        if (blocker.equals(Optional.of(MiningBlocker.RUNNING))) {
            control.afterCurrentRun(action);
            return;
        }
        if (mining.minedForCurrentIdentities() || blocker.isPresent()) {
            action.run();
            return;
        }
        switch (consent.mineFirst(parent)) {
            case MINE -> control.mine(action);
            case CONTINUE_WITHOUT -> action.run();
            case CANCEL -> { }
        }
    }
}
