package com.mcpscanner.checks.shared.random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RandomHexTest {

    @Test
    void writesTwoLowerCaseHexDigitsPerByte() {
        assertThat(RandomHex.of(8)).matches("[0-9a-f]{16}");
    }

    @Test
    void differsBetweenCalls() {
        assertThat(RandomHex.of(8)).isNotEqualTo(RandomHex.of(8));
    }
}
