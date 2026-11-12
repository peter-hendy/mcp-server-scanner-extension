package com.mcpscanner.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureDescriptionTest {

    @Test
    void describesFailureByItsMessage() {
        assertThat(FailureDescription.of(new IllegalArgumentException("server answered 2024-11-05")))
                .isEqualTo("IllegalArgumentException: server answered 2024-11-05");
    }

    @Test
    void fallsBackToTypeWhenMessageMissing() {
        assertThat(FailureDescription.of(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    @Test
    void usesCauseWhenWrapperAddsNoMessage() {
        assertThat(FailureDescription.of(new RuntimeException(new IllegalStateException("no endpoint event"))))
                .isEqualTo("IllegalStateException: no endpoint event");
    }
}
