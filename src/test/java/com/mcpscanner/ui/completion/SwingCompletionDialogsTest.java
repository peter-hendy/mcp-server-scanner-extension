package com.mcpscanner.ui.completion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwingCompletionDialogsTest {

    @Test
    void thePromptLabelShowsAServerSuppliedNameLiterally() {
        var label = SwingCompletionDialogs.promptLabel("<html><img src=x>");

        assertThat(label.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(label.getText()).contains("<html><img src=x>");
    }

    @Test
    void theResultIsReadOnlyPlainText() {
        var area = SwingCompletionDialogs.resultArea("<html>a\nb");

        assertThat(area.isEditable()).isFalse();
        assertThat(area.getText()).isEqualTo("<html>a\nb");
    }
}
