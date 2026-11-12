package com.mcpscanner.ui.widgets;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

public final class Placeholders {

    private Placeholders() {
    }

    public static JLabel centred(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(UIManager.getFont("Label.font"));
        label.setForeground(ThemeColors.muted());
        return label;
    }

    public static WrappingText wrapping(String text) {
        return new WrappingText(text).muted();
    }
}
