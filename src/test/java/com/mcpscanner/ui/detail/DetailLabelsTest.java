package com.mcpscanner.ui.detail;

import com.mcpscanner.ui.widgets.ThemeColors;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Font;

import static org.assertj.core.api.Assertions.assertThat;

class DetailLabelsTest {

    private static final String HOSTILE = "<html><img src=http://x>";

    @Test
    void aLiteralLabelShowsHtmlAsText() {
        JLabel label = DetailLabels.literal(HOSTILE);

        assertThat(label.getText()).isEqualTo(HOSTILE);
        assertThat(Components.htmlDisabled(label)).isTrue();
    }

    @Test
    void aMutedLabelIsLiteralAndUsesTheMutedColour() {
        JLabel label = DetailLabels.muted(HOSTILE);

        assertThat(Components.htmlDisabled(label)).isTrue();
        assertThat(label.getForeground()).isEqualTo(ThemeColors.muted());
    }

    @Test
    void boldKeepsTheSizeAndAddsWeight() {
        JLabel label = DetailLabels.literal("x");
        float size = label.getFont().getSize2D();

        JLabel bold = DetailLabels.bold(label);

        assertThat(bold).isSameAs(label);
        assertThat(bold.getFont().getStyle() & Font.BOLD).isEqualTo(Font.BOLD);
        assertThat(bold.getFont().getSize2D()).isEqualTo(size);
    }

    @Test
    void leftAlignedSetsTheAlignmentAndReturnsTheSameComponent() {
        JPanel panel = new JPanel();

        assertThat(DetailLabels.leftAligned(panel)).isSameAs(panel);
        assertThat(panel.getAlignmentX()).isEqualTo(Component.LEFT_ALIGNMENT);
    }
}
