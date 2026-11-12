package com.mcpscanner.ui.completion;

import com.mcpscanner.client.CompletionOutcome;

import java.util.ArrayList;
import java.util.List;

final class CompletionText {

    private static final String NO_VALUES = "The server suggested no values.";

    private CompletionText() {
    }

    static String render(CompletionOutcome outcome) {
        if (outcome instanceof CompletionOutcome.Values values) {
            return values(values);
        }
        if (outcome instanceof CompletionOutcome.Failed failed) {
            return failed(failed.reason());
        }
        return CompletionAvailability.NOT_ADVERTISED.unavailableReason();
    }

    static String failure(RuntimeException failure) {
        String message = failure.getMessage();
        return failed(message == null || message.isBlank() ? failure.getClass().getSimpleName() : message);
    }

    private static String values(CompletionOutcome.Values values) {
        List<String> lines = new ArrayList<>();
        values.values().stream().map(PrintableText::of).forEach(lines::add);
        if (lines.isEmpty()) {
            lines.add(NO_VALUES);
        }
        lines.add("");
        lines.add(shownSummary(values));
        if (values.hasMore() != null) {
            lines.add("More available: " + (values.hasMore() ? "yes" : "no"));
        }
        return String.join("\n", lines);
    }

    private static String shownSummary(CompletionOutcome.Values values) {
        String shown = "Shown: " + values.values().size();
        return values.total() == null ? shown : shown + " of " + values.total();
    }

    private static String failed(String reason) {
        return "The server could not be asked: " + PrintableText.of(reason);
    }
}
