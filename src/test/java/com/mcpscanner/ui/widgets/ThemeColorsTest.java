package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeColorsTest {

    private static final Color DARK_BACKGROUND = new Color(70, 73, 75);
    private static final Color LIGHT_BACKGROUND = new Color(242, 242, 242);

    @AfterEach
    void restore() {
        UIManager.put("Panel.background", null);
        UIManager.put("Colors.ui.text.error", null);
        UIManager.put("Label.disabledForeground", null);
        UIManager.put("Separator.foreground", null);
        UIManager.put("Colors.ui.text.link", null);
    }

    @Test
    void linksTakeBurpsLinkColour() {
        UIManager.put("Colors.ui.text.link", new Color(0xFF9E7A));

        assertThat(ThemeColors.link()).isEqualTo(new Color(0xFF9E7A));
    }

    @Test
    void linksFallBackToPortSwiggerOrange() {
        assertThat(ThemeColors.link()).isEqualTo(new Color(0xFF6633));
    }

    @Test
    void separatorFollowsTheLookAndFeel() {
        Color separator = new Color(0xD0D0D0);
        UIManager.put("Separator.foreground", separator);

        assertThat(ThemeColors.separator()).isEqualTo(separator);
    }

    @Test
    void errorTextIsLightenedUntilReadableOnADarkTheme() {
        UIManager.put("Panel.background", DARK_BACKGROUND);
        UIManager.put("Colors.ui.text.error", new Color(139, 60, 60));

        assertThat(Contrast.ratio(ThemeColors.error(), DARK_BACKGROUND)).isGreaterThanOrEqualTo(Contrast.READABLE);
        assertThat(ThemeColors.error().getRed()).isGreaterThan(ThemeColors.error().getGreen());
    }

    @Test
    void mutedTextIsDarkenedUntilReadableOnALightTheme() {
        UIManager.put("Panel.background", LIGHT_BACKGROUND);
        UIManager.put("Label.disabledForeground", new Color(0x9A9A9A));

        assertThat(Contrast.ratio(ThemeColors.muted(), LIGHT_BACKGROUND)).isGreaterThanOrEqualTo(Contrast.READABLE);
    }

    @Test
    void alreadyReadableColoursAreKept() {
        UIManager.put("Panel.background", LIGHT_BACKGROUND);
        UIManager.put("Colors.ui.text.error", new Color(0xB00020));

        assertThat(ThemeColors.error()).isEqualTo(new Color(0xB00020));
    }

    @Test
    void contrastRatioMatchesWcagForBlackOnWhite() {
        assertThat(Contrast.ratio(Color.BLACK, Color.WHITE)).isCloseTo(21.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
