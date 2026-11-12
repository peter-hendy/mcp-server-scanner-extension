package com.mcpscanner.ui.detail;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;

import static org.assertj.core.api.Assertions.assertThat;

class DetailSectionTest {

    @Test
    void aSectionWithoutContentIsHidden() {
        DetailSection section = new DetailSection("Resource");

        assertThat(section.isVisible()).isFalse();
        assertThat(section.hasContent()).isFalse();
    }

    @Test
    void showingAnEmptyGridKeepsItHidden() {
        DetailSection section = new DetailSection("Resource");

        section.show(new FieldGrid());

        assertThat(section.isVisible()).isFalse();
    }

    @Test
    void showingContentRevealsTheHeadingAndBody() {
        FieldGrid grid = new FieldGrid().addText("MIME type", "application/yaml");

        DetailSection section = DetailSection.of("Resource", grid);

        assertThat(section.isVisible()).isTrue();
        assertThat(section.hasContent()).isTrue();
        assertThat(Components.within(section, FieldGrid.class)).containsExactly(grid);
        assertThat(section.heading().getText()).isEqualTo("RESOURCE");
    }

    @Test
    void showingReplacesThePreviousBody() {
        JPanel previous = new JPanel();
        DetailSection section = DetailSection.of("Notes", previous);
        JPanel next = new JPanel();

        section.show(next);

        assertThat(Components.within(section, JPanel.class)).contains(next).doesNotContain(previous);
    }

    @Test
    void clearingHidesTheSection() {
        DetailSection section = DetailSection.of("Notes", new JPanel());

        section.show(null);

        assertThat(section.isVisible()).isFalse();
    }

    @Test
    void theHeadingIsHtmlDisabled() {
        DetailSection section = new DetailSection("<html><b>x</b>");

        assertThat(Components.within(section, JLabel.class)).allMatch(Components::htmlDisabled);
    }
}
