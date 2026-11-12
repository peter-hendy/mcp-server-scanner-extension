package com.mcpscanner.ui.detail;

import com.mcpscanner.ui.widgets.SwingHtmlGuard;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Font;

public final class DetailLabels {

    private DetailLabels() {
    }

    public static JLabel literal(String text) {
        return SwingHtmlGuard.disableHtml(new JLabel(text));
    }

    public static JLabel muted(String text) {
        JLabel label = literal(text);
        label.setForeground(ThemeColors.muted());
        return label;
    }

    public static JLabel bold(JLabel label) {
        Font font = label.getFont();
        if (font != null) {
            label.setFont(font.deriveFont(Font.BOLD));
        }
        return label;
    }

    public static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }
}
