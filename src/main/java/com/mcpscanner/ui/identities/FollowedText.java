package com.mcpscanner.ui.identities;

import javax.swing.text.JTextComponent;

final class FollowedText {

    private final JTextComponent field;
    private String shown = "";

    FollowedText(JTextComponent field) {
        this.field = field;
    }

    void seed(String value) {
        shown = value;
        field.setText(value);
    }

    void follow(String value) {
        if (field.getText().equals(value)) {
            shown = value;
        } else if (field.getText().equals(shown)) {
            seed(value);
        }
    }
}
