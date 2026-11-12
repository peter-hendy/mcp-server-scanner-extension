package com.mcpscanner.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

class FailureDescriptionTest {

    @Test
    void describesAFailureByItsMessage() {
        assertThat(FailureDescription.of(new IllegalStateException("boom"))).isEqualTo("boom");
    }

    @Test
    void namesAFailureThatHasNoMessage() {
        assertThat(FailureDescription.of(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    @Test
    void describesTheCauseOfACompletionFailure() {
        CompletionException wrapped = new CompletionException(new CompletionException(new IllegalStateException()));

        assertThat(FailureDescription.of(wrapped)).isEqualTo("IllegalStateException");
    }

    @Test
    void describesACompletionFailureWithoutACause() {
        assertThat(FailureDescription.of(new CompletionException("stopped", null))).isEqualTo("stopped");
    }
}
