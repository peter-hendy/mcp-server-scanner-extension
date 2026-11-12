package com.mcpscanner.ui;

import java.awt.Component;

@FunctionalInterface
public interface WriteCallPrompt {

    WriteCallPrompt MODAL = DestructiveScanConfirmation::promptLines;

    DestructiveScanConfirmation.Result ask(Component parent, DestructiveScanConfirmation.Lines lines);
}
