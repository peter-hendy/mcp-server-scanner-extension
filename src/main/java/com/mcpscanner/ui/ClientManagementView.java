package com.mcpscanner.ui;

import com.mcpscanner.ui.widgets.UiStyle;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Optional;
import java.util.function.Supplier;

final class ClientManagementView extends JPanel {

    private final Supplier<Clipboard> clipboard;
    private final JTextField clientUri = new JTextField();
    private final JButton copyToken = new JButton("Copy management token");
    private Optional<DcrRegistrations.Registration> registration = Optional.empty();

    ClientManagementView(Supplier<Clipboard> clipboard) {
        super(new BorderLayout(8, 0));
        this.clipboard = clipboard;
        setOpaque(false);
        clientUri.setEditable(false);
        clientUri.setBorder(null);
        clientUri.setOpaque(false);
        UiStyle.monospaced(clientUri);
        clientUri.setToolTipText("RFC 7592 registration_client_uri: where this registered client is read, "
                + "updated or deleted");
        copyToken.setToolTipText("Copy the RFC 7592 registration access token to the clipboard");
        copyToken.addActionListener(event -> registration.ifPresent(found ->
                this.clipboard.get().setContents(new StringSelection(found.managementToken()), null)));
        add(clientUri, BorderLayout.CENTER);
        add(copyToken, BorderLayout.EAST);
        setVisible(false);
    }

    void show(Optional<DcrRegistrations.Registration> shown) {
        registration = shown;
        clientUri.setText(shown.map(DcrRegistrations.Registration::clientUri).orElse(""));
        clientUri.setCaretPosition(0);
        setVisible(shown.isPresent());
    }

    String clientUri() {
        return clientUri.getText();
    }

    JButton copyButton() {
        return copyToken;
    }
}
