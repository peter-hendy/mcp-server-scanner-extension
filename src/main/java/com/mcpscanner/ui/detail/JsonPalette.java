package com.mcpscanner.ui.detail;

import com.mcpscanner.ui.widgets.Contrast;
import com.mcpscanner.ui.widgets.ThemeColors;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

final class JsonPalette {

    private static final Map<JsonTokenKind, Color> LIGHT = Map.of(
            JsonTokenKind.KEY, new Color(0x6639BA),
            JsonTokenKind.STRING, new Color(0x116329),
            JsonTokenKind.LITERAL, new Color(0x953800));
    private static final Map<JsonTokenKind, Color> DARK = Map.of(
            JsonTokenKind.KEY, new Color(0xD2A8FF),
            JsonTokenKind.STRING, new Color(0x7EE787),
            JsonTokenKind.LITERAL, new Color(0xFFA657));

    private final Map<JsonTokenKind, Color> colours = new EnumMap<>(JsonTokenKind.class);

    JsonPalette(Color background) {
        Map<JsonTokenKind, Color> base = ThemeColors.isDark(background) ? DARK : LIGHT;
        base.forEach((kind, colour) -> colours.put(kind, Contrast.readableOn(colour, background)));
        colours.put(JsonTokenKind.PUNCTUATION, Contrast.readableOn(ThemeColors.muted(), background));
    }

    Optional<Color> colourOf(JsonTokenKind kind) {
        return Optional.ofNullable(colours.get(kind));
    }
}
