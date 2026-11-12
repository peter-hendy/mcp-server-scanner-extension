package com.mcpscanner.ui.widgets;

import java.awt.Color;

public final class Contrast {

    public static final double READABLE = 4.5;
    private static final double BLEND_STEP = 0.05;

    private Contrast() {
    }

    public static double ratio(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static Color readableOn(Color foreground, Color background) {
        Color target = ThemeColors.isDark(background) ? Color.WHITE : Color.BLACK;
        Color candidate = foreground;
        for (double share = BLEND_STEP; ratio(candidate, background) < READABLE && share <= 1.0; share += BLEND_STEP) {
            candidate = blend(foreground, target, share);
        }
        return candidate;
    }

    public static Color blend(Color from, Color to, double share) {
        return new Color(mix(from.getRed(), to.getRed(), share), mix(from.getGreen(), to.getGreen(), share),
                mix(from.getBlue(), to.getBlue(), share));
    }

    private static int mix(int from, int to, double share) {
        return (int) Math.round(from + (to - from) * share);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double normalised = value / 255.0;
        return normalised <= 0.03928 ? normalised / 12.92 : Math.pow((normalised + 0.055) / 1.055, 2.4);
    }
}
