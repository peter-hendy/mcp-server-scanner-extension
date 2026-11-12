package com.mcpscanner.ui.widgets;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.Optional;
import java.util.function.Supplier;

public final class CopyableSecret extends JPanel {

    private final SecretField secret;
    private final JButton copy = new JButton("Copy");

    public CopyableSecret(String noun, Supplier<Clipboard> clipboard) {
        super(new BorderLayout(4, 0));
        this.secret = SecretField.readOnly(noun);
        setOpaque(false);
        UiStyle.monospaced(secret.textField());
        copy.setToolTipText("Copy the " + noun + " to the clipboard");
        copy.addActionListener(event -> clipboard.get().setContents(new StringSelection(secret.value()), null));
        add(secret, BorderLayout.CENTER);
        add(copy, BorderLayout.EAST);
        setVisible(false);
    }

    public void show(Optional<String> value) {
        secret.mask();
        display(value);
    }

    public void display(Optional<String> value) {
        secret.setValue(value.orElse(""));
        setVisible(value.filter(shown -> !shown.isEmpty()).isPresent());
    }

    public String value() {
        return secret.value();
    }

    public SecretField secret() {
        return secret;
    }

    public JButton copyButton() {
        return copy;
    }
}
