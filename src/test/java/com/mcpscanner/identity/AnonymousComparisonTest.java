package com.mcpscanner.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousComparisonTest {

    @Test
    void namesAnonymousAfterTheComparedUsers() {
        assertThat(AnonymousComparison.alongside("bob, carol")).isEqualTo("bob, carol + Anonymous");
    }

    @Test
    void anonymousIsComparedOnlyAlongsideAComparedUser() {
        assertThat(AnonymousComparison.comparedIdentities(0)).isZero();
        assertThat(AnonymousComparison.comparedIdentities(2)).isEqualTo(3);
    }
}
