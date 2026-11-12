package com.mcpscanner.ui.detail;

import com.mcpscanner.ui.widgets.Contrast;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class ChipTest {

    private static final Color DARK_BACKGROUND = new Color(0x3C3F41);
    private static final Color LIGHT_BACKGROUND = new Color(0xF2F2F2);

    @AfterEach
    void restore() {
        UIManager.put("Panel.background", null);
    }

    @Test
    void serverTextIsRenderedLiterally() {
        Chip chip = Chip.neutral("<html><img src=http://x>");

        assertThat(Components.htmlDisabled(chip)).isTrue();
        assertThat(chip.getText()).isEqualTo("<html><img src=http://x>");
    }

    @ParameterizedTest
    @EnumSource(ChipVariant.class)
    void textIsReadableOnItsTintOnALightTheme(ChipVariant variant) {
        UIManager.put("Panel.background", LIGHT_BACKGROUND);

        Chip chip = new Chip("label", variant);

        assertThat(Contrast.ratio(chip.getForeground(), chip.getBackground())).isGreaterThanOrEqualTo(Contrast.READABLE);
    }

    @ParameterizedTest
    @EnumSource(ChipVariant.class)
    void textIsReadableOnItsTintOnADarkTheme(ChipVariant variant) {
        UIManager.put("Panel.background", DARK_BACKGROUND);

        Chip chip = new Chip("label", variant);

        assertThat(Contrast.ratio(chip.getForeground(), chip.getBackground())).isGreaterThanOrEqualTo(Contrast.READABLE);
    }

    @Test
    void tintsFollowTheThemeBrightness() {
        UIManager.put("Panel.background", DARK_BACKGROUND);
        Color dark = Chip.neutral("x").getBackground();
        UIManager.put("Panel.background", LIGHT_BACKGROUND);
        Color light = Chip.neutral("x").getBackground();

        assertThat(dark.getRed()).isLessThan(light.getRed());
    }

    @Test
    void attentionIsWarmAndPositiveIsGreen() {
        UIManager.put("Panel.background", LIGHT_BACKGROUND);

        Color attention = Chip.attention("Required").getBackground();
        Color positive = Chip.positive("Read-only").getBackground();

        assertThat(attention.getRed()).isGreaterThan(attention.getBlue());
        assertThat(positive.getGreen()).isGreaterThan(positive.getRed()).isGreaterThan(positive.getBlue());
    }

    @Test
    void variantsAreDistinguishable() {
        UIManager.put("Panel.background", LIGHT_BACKGROUND);

        assertThat(Chip.attention("a").getBackground())
                .isNotEqualTo(Chip.positive("a").getBackground())
                .isNotEqualTo(Chip.neutral("a").getBackground());
    }

    @Test
    void reapplyingTheThemeDoesNotKeepShrinkingTheText() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        try {
            Chip chip = Chip.neutral("x");
            float size = chip.getFont().getSize2D();

            chip.updateUI();
            chip.updateUI();

            assertThat(chip.getFont().getSize2D()).isEqualTo(size);
        } finally {
            UIManager.setLookAndFeel(original);
        }
    }
}
