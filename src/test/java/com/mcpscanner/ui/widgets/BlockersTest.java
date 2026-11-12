package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BlockersTest {

    private final JButton control = new JButton();

    @Test
    void blockedControlIsDisabledAndExplainsWhy() {
        Blockers.apply(control, Optional.of("Connect first"), "Go");

        assertThat(control.isEnabled()).isFalse();
        assertThat(control.getToolTipText()).isEqualTo("Connect first");
    }

    @Test
    void unblockedControlIsEnabledWithItsUsualTooltip() {
        control.setEnabled(false);

        Blockers.apply(control, Optional.empty(), "Go");

        assertThat(control.isEnabled()).isTrue();
        assertThat(control.getToolTipText()).isEqualTo("Go");
    }

    @Test
    void unblockedControlWithoutUsualTooltipHasNone() {
        control.setToolTipText("stale");

        Blockers.apply(control, Optional.empty(), null);

        assertThat(control.getToolTipText()).isNull();
    }
}
