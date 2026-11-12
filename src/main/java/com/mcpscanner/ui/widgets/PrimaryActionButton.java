package com.mcpscanner.ui.widgets;

import javax.swing.JButton;

public final class PrimaryActionButton extends JButton {

    public PrimaryActionButton(String label) {
        super(label);
        setPrimary(true);
    }

    public void setPrimary(boolean primary) {
        UiStyle.styleClass(this, primary ? UiStyle.PRIMARY : null);
    }

    public boolean isPrimary() {
        return UiStyle.PRIMARY.equals(getClientProperty(UiStyle.STYLE_CLASS));
    }
}
