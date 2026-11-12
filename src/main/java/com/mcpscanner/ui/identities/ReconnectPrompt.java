package com.mcpscanner.ui.identities;

import java.awt.Component;

@FunctionalInterface
public interface ReconnectPrompt {

    ReconnectPrompt MODAL = ReconnectDialog::confirm;

    boolean confirm(Component parent, String user, String previousUser);
}
