package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrimaryActionButtonTest {

    @Test
    void primaryButtonUsesTheLookAndFeelPrimaryStyleInsteadOfCustomPainting() {
        PrimaryActionButton button = new PrimaryActionButton("Scan");

        assertThat(button.getText()).isEqualTo("Scan");
        assertThat(button.getClientProperty(UiStyle.STYLE_CLASS)).isEqualTo(UiStyle.PRIMARY);
        assertThat(button.isBorderPainted()).isTrue();
        assertThat(button.isFocusPainted()).isTrue();
        assertThat(button.isContentAreaFilled()).isTrue();
    }

    @Test
    void demotedButtonFallsBackToTheStandardButtonStyle() {
        PrimaryActionButton button = new PrimaryActionButton("Connect");

        button.setPrimary(false);

        assertThat(button.getClientProperty(UiStyle.STYLE_CLASS)).isNull();
        assertThat(button.isPrimary()).isFalse();
    }
}
