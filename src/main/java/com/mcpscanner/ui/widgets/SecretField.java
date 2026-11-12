package com.mcpscanner.ui.widgets;

import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;

public final class SecretField extends JPanel {

    private static final String PLACEHOLDER = "JTextField.placeholderText";

    private final JPasswordField field = new JPasswordField();
    private final RevealToggle toggle;
    private final EchoMask echo = new EchoMask(field);

    public SecretField(String noun) {
        super(new BorderLayout(4, 0));
        setOpaque(false);
        field.setColumns(1);
        field.getAccessibleContext().setAccessibleName(noun);
        toggle = new RevealToggle(noun);
        toggle.matchHeight(field.getPreferredSize().height);
        toggle.addItemListener(event -> applyReveal());
        add(field, BorderLayout.CENTER);
        add(toggle, BorderLayout.EAST);
        applyReveal();
    }

    public static SecretField readOnly(String noun) {
        SecretField secret = new SecretField(noun);
        secret.field.setEditable(false);
        return secret;
    }

    public String value() {
        return new String(field.getPassword());
    }

    public void setValue(String value) {
        field.setText(value);
        field.setCaretPosition(0);
    }

    public void onChange(Runnable listener) {
        field.getDocument().addDocumentListener(DocumentChanges.onChange(listener));
    }

    public void reveal(boolean show) {
        toggle.setSelected(show);
    }

    public boolean revealed() {
        return toggle.isSelected();
    }

    public void mask() {
        reveal(false);
    }

    public void setPlaceholder(String placeholder) {
        field.putClientProperty(PLACEHOLDER, placeholder);
    }

    public JPasswordField textField() {
        return field;
    }

    public JToggleButton toggle() {
        return toggle;
    }

    private void applyReveal() {
        echo.apply(revealed());
    }
}
