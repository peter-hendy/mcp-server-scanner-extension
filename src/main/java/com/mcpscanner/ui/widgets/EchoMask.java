package com.mcpscanner.ui.widgets;

import javax.swing.JPasswordField;

final class EchoMask {

    private static final char PLAIN = 0;

    private final JPasswordField field;
    private final char mask;

    EchoMask(JPasswordField field) {
        this.field = field;
        this.mask = field.getEchoChar();
    }

    void apply(boolean revealed) {
        field.setEchoChar(revealed ? PLAIN : mask);
    }
}
