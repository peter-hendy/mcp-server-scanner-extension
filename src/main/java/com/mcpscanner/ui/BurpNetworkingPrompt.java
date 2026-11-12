package com.mcpscanner.ui;

import java.awt.Component;

@FunctionalInterface
public interface BurpNetworkingPrompt {

    BurpNetworkingPrompt MODAL = BurpNetworkingDialog::offerToTurnOff;

    boolean offerToTurnOff(Component parent);
}
