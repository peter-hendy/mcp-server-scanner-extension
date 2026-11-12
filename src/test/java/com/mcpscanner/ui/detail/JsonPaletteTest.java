package com.mcpscanner.ui.detail;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPaletteTest {

    private static final float MIN_HUE_DISTANCE_DEGREES = 60f;

    @ParameterizedTest
    @ValueSource(ints = {0xFFFFFF, 0xF2F2F2, 0x3C3F41, 0x2B2B2B, 0x1E1F22})
    void keysAndStringsHaveClearlyDifferentHues(int background) {
        JsonPalette palette = new JsonPalette(new Color(background));

        float distance = hueDistanceDegrees(colour(palette, JsonTokenKind.KEY), colour(palette, JsonTokenKind.STRING));

        assertThat(distance).isGreaterThanOrEqualTo(MIN_HUE_DISTANCE_DEGREES);
    }

    @ParameterizedTest
    @ValueSource(ints = {0xFFFFFF, 0xF2F2F2, 0x3C3F41, 0x2B2B2B, 0x1E1F22})
    void literalsDifferInHueFromKeysAndStrings(int background) {
        JsonPalette palette = new JsonPalette(new Color(background));
        Color literal = colour(palette, JsonTokenKind.LITERAL);

        assertThat(hueDistanceDegrees(literal, colour(palette, JsonTokenKind.KEY)))
                .isGreaterThanOrEqualTo(MIN_HUE_DISTANCE_DEGREES);
        assertThat(hueDistanceDegrees(literal, colour(palette, JsonTokenKind.STRING)))
                .isGreaterThanOrEqualTo(MIN_HUE_DISTANCE_DEGREES);
    }

    private static Color colour(JsonPalette palette, JsonTokenKind kind) {
        return palette.colourOf(kind).orElseThrow();
    }

    private static float hueDistanceDegrees(Color first, Color second) {
        float difference = Math.abs(hue(first) - hue(second)) * 360f;
        return Math.min(difference, 360f - difference);
    }

    private static float hue(Color colour) {
        return Color.RGBtoHSB(colour.getRed(), colour.getGreen(), colour.getBlue(), null)[0];
    }
}
