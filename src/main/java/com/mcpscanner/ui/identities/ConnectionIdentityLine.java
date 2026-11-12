package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.ConnectedIdentity;
import com.mcpscanner.identity.StaleConnection;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.util.Optional;

public final class ConnectionIdentityLine extends JPanel {

    private static final int GAP = 8;

    private final IdentityUiServices services;
    private final JLabel identity = SwingHtmlGuard.disableHtml(new JLabel());
    private final StaleConnectionButton action;
    private final Runnable unsubscribe;

    ConnectionIdentityLine(IdentityUiServices services) {
        this.services = services;
        this.action = new StaleConnectionButton(new PrimaryUserChoice(services, () -> this));
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        setOpaque(false);
        add(identity);
        add(Box.createRigidArea(new Dimension(GAP, action.getPreferredSize().height)));
        add(action);
        this.unsubscribe = services.onChange(() -> SwingUtilities.invokeLater(this::refresh));
        refresh();
    }

    public void refresh() {
        Optional<ConnectedIdentity> connected = services.connection().connectedIdentity();
        setVisible(connected.isPresent());
        connected.ifPresent(this::describe);
    }

    public void dispose() {
        unsubscribe.run();
    }

    String identityText() {
        return identity.getText();
    }

    String identityTooltip() {
        return identity.getToolTipText();
    }

    JButton actionButton() {
        return action;
    }

    private void describe(ConnectedIdentity connected) {
        Optional<StaleConnection> stale = services.staleConnection();
        identity.setText(ConnectionIdentityText.line(connected));
        identity.setForeground(stale.isPresent() ? ThemeColors.warning() : UIManager.getColor("Label.foreground"));
        identity.setToolTipText(stale.map(ConnectionIdentityText::hint).map(SwingHtmlGuard::escapeHtml).orElse(null));
        action.show(stale);
        revalidate();
        repaint();
    }
}
