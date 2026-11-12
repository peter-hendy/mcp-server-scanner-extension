package com.mcpscanner.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

final class ClientTab {

    private ClientTab() {
    }

    static JComponent of(ClientIdentityPanel identity, OAuthClientSection oauthClient) {
        JPanel sections = new JPanel(new BorderLayout());
        sections.add(identity, BorderLayout.NORTH);
        sections.add(oauthClient, BorderLayout.CENTER);
        JPanel tab = new JPanel(new BorderLayout());
        tab.add(sections, BorderLayout.NORTH);
        return tab;
    }
}
