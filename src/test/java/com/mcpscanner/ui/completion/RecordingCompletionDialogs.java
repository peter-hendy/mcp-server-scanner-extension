package com.mcpscanner.ui.completion;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RecordingCompletionDialogs implements CompletionDialogs {

    record Shown(Component parent, String title, String text) {
    }

    private final List<String> askedFor = new ArrayList<>();
    private final List<Shown> shown = new ArrayList<>();
    private Optional<String> typedValue = Optional.of("");

    void typeValue(String value) {
        typedValue = Optional.of(value);
    }

    void cancelInput() {
        typedValue = Optional.empty();
    }

    List<String> askedFor() {
        return List.copyOf(askedFor);
    }

    List<Shown> shown() {
        return List.copyOf(shown);
    }

    @Override
    public Optional<String> askCurrentValue(Component parent, String argumentName) {
        askedFor.add(argumentName);
        return typedValue;
    }

    @Override
    public void showResult(Component parent, String title, String text) {
        shown.add(new Shown(parent, title, text));
    }
}
