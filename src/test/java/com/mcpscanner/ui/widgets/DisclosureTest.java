package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;

import static org.assertj.core.api.Assertions.assertThat;

class DisclosureTest {

    private final JLabel content = new JLabel("inside");
    private final Disclosure disclosure = new Disclosure("Owned data (optional)", content);

    @Test
    void startsCollapsedShowingOnlyTheTitle() {
        assertThat(disclosure.isExpanded()).isFalse();
        assertThat(content.isVisible()).isFalse();
        assertThat(disclosure.toggle().getText()).isEqualTo("▸ Owned data (optional)");
    }

    @Test
    void clickingTheTitleExpandsAndCollapsesTheContent() {
        disclosure.toggle().doClick();

        assertThat(disclosure.isExpanded()).isTrue();
        assertThat(content.isVisible()).isTrue();
        assertThat(disclosure.toggle().getText()).isEqualTo("▾ Owned data (optional)");

        disclosure.toggle().doClick();

        assertThat(content.isVisible()).isFalse();
    }

    @Test
    void theTitleCanSummariseWhatIsInside() {
        disclosure.setTitle("Owned data: 2 objects, marker set");

        assertThat(disclosure.toggle().getText()).isEqualTo("▸ Owned data: 2 objects, marker set");
    }

    @Test
    void expandingFromCodeShowsTheContent() {
        disclosure.expand();

        assertThat(content.isVisible()).isTrue();
    }
}
