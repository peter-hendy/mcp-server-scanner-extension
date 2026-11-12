package com.mcpscanner.checks.api.crossidentity;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SameShapeValueTest {

    private final SameShapeValue values = new SameShapeValue(new Random(7));

    @Test
    void keepsTheCharacterClassOfEveryPositionAndEverySeparator() {
        assertThat(values.like("c0a11ce0-0000-4000-8000-000000055544"))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .isNotEqualTo("c0a11ce0-0000-4000-8000-000000055544");
        assertThat(values.like("note-alice-1")).matches("[a-z]{4}-[a-z]{5}-[0-9]");
        assertThat(values.like("INV_2024")).matches("[A-Z]{3}_[0-9]{4}");
    }

    @Test
    void neverReturnsTheOriginal() {
        for (int attempt = 0; attempt < 50; attempt++) {
            assertThat(values.like("7")).matches("[0-9]").isNotEqualTo("7");
        }
    }

    @Test
    void aWholeNumberKeepsItsDigitCount() {
        assertThat(values.like(42)).isBetween(10L, 99L).isNotEqualTo(42L);
    }
}
