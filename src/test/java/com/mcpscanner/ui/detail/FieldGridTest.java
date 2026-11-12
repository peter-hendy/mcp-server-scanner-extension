package com.mcpscanner.ui.detail;

import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import java.awt.Font;

import static org.assertj.core.api.Assertions.assertThat;

class FieldGridTest {

    private static final String HOSTILE = "<html><img src=http://x>";

    @Test
    void aNewGridIsEmptyAndHidden() {
        FieldGrid grid = new FieldGrid();

        assertThat(grid.isEmpty()).isTrue();
        assertThat(grid.isVisible()).isFalse();
    }

    @Test
    void nullAndBlankValuesAreSkipped() {
        FieldGrid grid = new FieldGrid()
                .addText("Title", null)
                .addText("Version", "  ")
                .addMonospaced("URI", "")
                .addComponent("Icons", null);

        assertThat(grid.isEmpty()).isTrue();
        assertThat(grid.rowCount()).isZero();
    }

    @Test
    void aRowMakesTheGridVisible() {
        FieldGrid grid = new FieldGrid().addText("Version", "1.0.0");

        assertThat(grid.isEmpty()).isFalse();
        assertThat(grid.isVisible()).isTrue();
        assertThat(Components.shownTexts(grid)).contains("Version", "1.0.0");
    }

    @Test
    void monospacedRowsUseAMonospacedFont() {
        FieldGrid grid = new FieldGrid().addMonospaced("URI", "file:///srv/a");

        JTextComponent value = valueShowing(grid, "file:///srv/a");

        assertThat(value.getFont().getFamily()).isEqualTo(new Font(Font.MONOSPACED, Font.PLAIN, 12).getFamily());
    }

    @Test
    void componentRowsHoldTheGivenComponent() {
        JLabel chip = Chip.neutral("user");

        FieldGrid grid = new FieldGrid().addComponent("Audience", chip);

        assertThat(Components.within(grid, JComponent.class)).contains(chip);
        assertThat(grid.rowCount()).isEqualTo(1);
    }

    @Test
    void serverSuppliedLabelsAndValuesRenderLiterally() {
        FieldGrid grid = new FieldGrid().addText(HOSTILE, HOSTILE).addMonospaced(HOSTILE, HOSTILE);

        assertThat(Components.within(grid, JLabel.class)).isNotEmpty().allMatch(Components::htmlDisabled);
        assertThat(Components.within(grid, JLabel.class)).extracting(JLabel::getText).containsOnly(HOSTILE);
        assertThat(Components.within(grid, JTextComponent.class)).extracting(JTextComponent::getText)
                .containsOnly(HOSTILE);
    }

    private static JTextComponent valueShowing(FieldGrid grid, String text) {
        return Components.within(grid, JTextComponent.class).stream()
                .filter(component -> component.getText().equals(text))
                .findFirst()
                .orElseThrow();
    }
}
