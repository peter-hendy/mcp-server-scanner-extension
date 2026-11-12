package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class FadedWhenDisabledTest {

    @Test
    void disabledTextSitsClearlyBetweenTheTextAndBackgroundColours() {
        JTextField field = new JTextField();
        field.setForeground(new Color(40, 40, 40));
        field.setBackground(new Color(250, 250, 250));

        FadedWhenDisabled.install(field);

        assertThat(field.getDisabledTextColor().getRed()).isBetween(140, 170);
    }

    @Test
    void refadesWhenTheThemeChanges() {
        JTextField field = new JTextField();
        field.setForeground(new Color(40, 40, 40));
        field.setBackground(new Color(250, 250, 250));
        FadedWhenDisabled.install(field);
        Color before = field.getDisabledTextColor();
        field.setForeground(Color.BLACK);
        field.setBackground(Color.WHITE);

        field.updateUI();

        assertThat(field.getDisabledTextColor()).isNotEqualTo(before)
                .isEqualTo(Contrast.blend(Color.BLACK, Color.WHITE, 0.55));
    }

    @Test
    void comboBoxGetsAFadedDisabledForegroundStyle() {
        JComboBox<String> combo = new JComboBox<>(new String[] {"Auto"});
        combo.setForeground(Color.BLACK);
        combo.setBackground(Color.WHITE);

        FadedWhenDisabled.install(combo);

        assertThat(combo.getClientProperty(FadedWhenDisabled.FLATLAF_STYLE))
                .isEqualTo("disabledForeground: " + hex(Contrast.blend(Color.BLACK, Color.WHITE, 0.55)));
    }

    @Test
    void checkBoxGetsAFadedDisabledTextStyle() {
        JCheckBox box = new JCheckBox("Detect");
        box.setForeground(Color.BLACK);
        box.setBackground(Color.WHITE);

        FadedWhenDisabled.install(box);

        assertThat(box.getClientProperty(FadedWhenDisabled.FLATLAF_STYLE))
                .isEqualTo("disabledText: " + hex(Contrast.blend(Color.BLACK, Color.WHITE, 0.55)));
    }

    private static String hex(Color colour) {
        return String.format("#%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue());
    }
}
