package com.mcpscanner.ui.widgets;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

final class SideTabStyle {

    private static final Color ACCENT_FALLBACK = new Color(0xE8612C);
    private static final Insets INSETS_FALLBACK = new Insets(4, 12, 4, 12);
    private static final int HEIGHT_FALLBACK = 32;
    private static final int ACCENT_THICKNESS_FALLBACK = 3;

    private SideTabStyle() {
    }

    static Font font(Font fallback) {
        Font font = UIManager.getFont("TabbedPane.font");
        return font != null ? font : fallback;
    }

    static Color accent() {
        return firstColor(ACCENT_FALLBACK, "TabbedPane.underlineColor", "Component.accentColor",
                "Component.focusColor");
    }

    static Color hoverAccent() {
        return ThemeColors.muted();
    }

    static Color selectedBackground() {
        return firstColor(null, "TabbedPane.selectedBackground");
    }

    static Color selectedForeground(Color fallback) {
        return firstColor(fallback, "TabbedPane.selectedForeground");
    }

    static Color hoverForeground(Color fallback) {
        return firstColor(fallback, "TabbedPane.hoverForeground");
    }

    static Insets padding() {
        Insets insets = UIManager.getInsets("TabbedPane.tabInsets");
        return insets != null ? insets : INSETS_FALLBACK;
    }

    static int thickness() {
        return positiveInt("TabbedPane.tabHeight", HEIGHT_FALLBACK);
    }

    static int accentThickness() {
        return positiveInt("TabbedPane.tabSelectionHeight", ACCENT_THICKNESS_FALLBACK);
    }

    private static int positiveInt(String key, int fallback) {
        int value = UIManager.getInt(key);
        return value > 0 ? value : fallback;
    }

    private static Color firstColor(Color fallback, String... keys) {
        for (String key : keys) {
            Color color = UIManager.getColor(key);
            if (color != null) {
                return color;
            }
        }
        return fallback;
    }
}
