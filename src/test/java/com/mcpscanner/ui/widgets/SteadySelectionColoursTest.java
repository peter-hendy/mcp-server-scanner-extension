package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class SteadySelectionColoursTest {

    @Test
    void pinsTheActiveSelectionColoursSoTheLookAndFeelCannotSwapThemOnFocusLoss() {
        JTable table = SteadySelectionColours.install(new JTable());

        assertThat(table.getSelectionBackground()).isEqualTo(UIManager.getColor("Table.selectionBackground"))
                .isNotInstanceOf(UIResource.class);
        assertThat(table.getSelectionForeground()).isEqualTo(UIManager.getColor("Table.selectionForeground"))
                .isNotInstanceOf(UIResource.class);
    }

    @Test
    void followsAThemeChange() {
        JTable table = SteadySelectionColours.install(new JTable());
        Color original = UIManager.getColor("Table.selectionBackground");
        try {
            UIManager.put("Table.selectionBackground", new javax.swing.plaf.ColorUIResource(Color.MAGENTA));
            table.updateUI();

            assertThat(table.getSelectionBackground()).isEqualTo(Color.MAGENTA);
        } finally {
            UIManager.put("Table.selectionBackground", original);
        }
    }
}
