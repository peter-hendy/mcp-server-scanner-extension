package com.mcpscanner.ui.completion;

import com.mcpscanner.client.CompletionOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionTextTest {

    @Test
    void listsEachValueOnItsOwnLineThenTheCountsTheServerGave() {
        assertThat(CompletionText.render(new CompletionOutcome.Values(List.of("python", "pytorch"), 10, true)))
                .isEqualTo("python\npytorch\n\nShown: 2 of 10\nMore available: yes");
    }

    @Test
    void omitsCountsTheServerDidNotGive() {
        assertThat(CompletionText.render(new CompletionOutcome.Values(List.of("a"), null, null)))
                .isEqualTo("a\n\nShown: 1");
    }

    @Test
    void saysSoWhenThereAreNoValues() {
        assertThat(CompletionText.render(new CompletionOutcome.Values(List.of(), 0, false)))
                .isEqualTo("The server suggested no values.\n\nShown: 0 of 0\nMore available: no");
    }

    @Test
    void aValueCannotSpoofExtraLinesOrHideCharacters() {
        assertThat(CompletionText.render(new CompletionOutcome.Values(
                List.of("one\ntwo", "tab\there", "rtl‮evil", "back\\slash", "bell\u0007"), null, null)))
                .startsWith("one\\ntwo\ntab\\there\nrtl\\u202Eevil\nback\\\\slash\nbell\\u0007\n\n");
    }

    @Test
    void htmlLikeValuesStayLiteralText() {
        assertThat(CompletionText.render(new CompletionOutcome.Values(List.of("<html><b>x"), null, null)))
                .startsWith("<html><b>x\n");
    }

    @Test
    void describesAFailureAndTheMissingCapability() {
        assertThat(CompletionText.render(new CompletionOutcome.Failed("JSON-RPC error -32602: bad\nline")))
                .isEqualTo("The server could not be asked: JSON-RPC error -32602: bad\\nline");
        assertThat(CompletionText.render(new CompletionOutcome.NotAdvertised()))
                .isEqualTo(CompletionAvailability.NOT_ADVERTISED.unavailableReason());
    }

    @Test
    void describesAnExceptionWithoutAMessageByItsKind() {
        assertThat(CompletionText.failure(new IllegalStateException()))
                .isEqualTo("The server could not be asked: IllegalStateException");
    }
}
