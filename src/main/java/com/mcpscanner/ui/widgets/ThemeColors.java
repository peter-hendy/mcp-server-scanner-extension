package com.mcpscanner.ui.widgets;

import javax.swing.UIManager;
import java.awt.Color;

public final class ThemeColors {

    private static final Color HYPERLINK_DARK = new Color(0x4FC3F7);
    private static final Color HYPERLINK_LIGHT = new Color(0x1565C0);
    private static final Color PORTSWIGGER_ORANGE = new Color(0xFF6633);
    private static final Color SUCCESS_FALLBACK = new Color(0x4CAF50);
    private static final Color WARNING_FALLBACK = new Color(0xC7922B);
    private static final Color ERROR_FALLBACK = new Color(0xD95757);
    private static final Color MUTED_FALLBACK = new Color(0x8C8C8C);

    private ThemeColors() {
    }

    public static boolean isDark(Color background) {
        if (background == null) {
            return false;
        }
        double luminance = (0.2126 * background.getRed()
                + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue()) / 255.0;
        return luminance < 0.5;
    }

    public static Color hyperlinkColor(boolean dark) {
        Color fromLaf = UIManager.getColor("Hyperlink.linkColor");
        if (fromLaf != null) {
            return fromLaf;
        }
        return dark ? HYPERLINK_DARK : HYPERLINK_LIGHT;
    }

    public static Color link() {
        return firstDefined(PORTSWIGGER_ORANGE, "Colors.ui.text.link");
    }

    public static Color success() {
        return firstDefined(SUCCESS_FALLBACK, "Colors.ui.icon.success", "Actions.Green");
    }

    public static Color warning() {
        return firstDefined(WARNING_FALLBACK, "Colors.ui.icon.warning", "Component.warning.focusedBorderColor");
    }

    public static Color error() {
        return readable(firstDefined(ERROR_FALLBACK, "Colors.ui.text.error", "Label.errorForeground",
                "Component.error.focusedBorderColor"));
    }

    public static Color muted() {
        return readable(firstDefined(MUTED_FALLBACK, "Label.disabledForeground"));
    }

    public static Color separator() {
        return firstDefined(muted(), "Separator.foreground");
    }

    private static Color readable(Color foreground) {
        return Contrast.readableOn(foreground, firstDefined(Color.WHITE, "Panel.background"));
    }

    private static Color firstDefined(Color fallback, String... keys) {
        for (String key : keys) {
            Color color = UIManager.getColor(key);
            if (color != null) {
                return color;
            }
        }
        return fallback;
    }
}
