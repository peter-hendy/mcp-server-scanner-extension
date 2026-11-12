package com.mcpscanner.ui.completion;

import java.awt.Component;
import java.util.Optional;

interface CompletionDialogs {

    Optional<String> askCurrentValue(Component parent, String argumentName);

    void showResult(Component parent, String title, String text);
}
