package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.StaleConnection;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.JButton;
import java.util.Optional;

final class StaleConnectionButton extends JButton {

    private Optional<StaleConnection> stale = Optional.empty();

    StaleConnectionButton(PrimaryUserChoice primaryChoice) {
        SwingHtmlGuard.disableHtml(this);
        addActionListener(event -> stale.ifPresent(primaryChoice::bringUpToDate));
        setVisible(false);
    }

    void show(Optional<StaleConnection> current) {
        stale = current;
        setVisible(current.isPresent());
        current.ifPresent(this::describe);
    }

    private void describe(StaleConnection current) {
        setText(ConnectionIdentityText.RECONNECT);
        setToolTipText(SwingHtmlGuard.escapeHtml(ConnectionIdentityText.hint(current)));
    }
}
