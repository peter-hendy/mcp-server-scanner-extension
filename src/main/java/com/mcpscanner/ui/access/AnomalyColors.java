package com.mcpscanner.ui.access;

import com.mcpscanner.ui.widgets.Contrast;
import com.mcpscanner.ui.widgets.ThemeColors;

import java.awt.Color;

final class AnomalyColors {

    private static final double TINT = 0.28;

    private AnomalyColors() {
    }

    static Color background(Color base) {
        Color error = ThemeColors.error();
        return new Color(mix(base.getRed(), error.getRed()), mix(base.getGreen(), error.getGreen()),
                mix(base.getBlue(), error.getBlue()));
    }

    static Color foreground(Color background) {
        return Contrast.readableOn(ThemeColors.error(), background);
    }

    private static int mix(int from, int to) {
        return (int) Math.round(from + (to - from) * TINT);
    }
}
