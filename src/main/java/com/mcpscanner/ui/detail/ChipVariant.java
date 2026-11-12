package com.mcpscanner.ui.detail;

import com.mcpscanner.ui.widgets.Contrast;
import com.mcpscanner.ui.widgets.ThemeColors;

import java.awt.Color;
import java.util.function.Supplier;

public enum ChipVariant {
    NEUTRAL(ThemeColors::muted),
    ATTENTION(ThemeColors::warning),
    POSITIVE(ThemeColors::success);

    private static final double BACKGROUND_SHARE = 0.78;

    private final Supplier<Color> accent;

    ChipVariant(Supplier<Color> accent) {
        this.accent = accent;
    }

    Color tintOn(Color background) {
        return Contrast.blend(accent.get(), background, BACKGROUND_SHARE);
    }

    Color textOn(Color tint) {
        return Contrast.readableOn(accent.get(), tint);
    }
}
