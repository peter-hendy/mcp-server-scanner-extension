package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluralsTest {

    @Test
    void oneKeepsTheNounSingular() {
        assertThat(Plurals.count(1, "issue")).isEqualTo("1 issue");
    }

    @Test
    void zeroAndManyPluraliseTheNoun() {
        assertThat(Plurals.count(0, "issue")).isEqualTo("0 issues");
        assertThat(Plurals.count(3, "scan task")).isEqualTo("3 scan tasks");
    }
}
