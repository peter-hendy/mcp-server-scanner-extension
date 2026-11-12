package com.mcpscanner.ui.completion;

import com.mcpscanner.client.CompletionReference;
import com.mcpscanner.client.CompletionRequest;

import java.util.Optional;
import java.util.function.Function;

record CompletionTarget(CompletionReference reference, String argumentName) {

    static Optional<CompletionTarget> forPrompt(String promptName, String argumentName) {
        return of(promptName, argumentName, CompletionReference.Prompt::new);
    }

    static Optional<CompletionTarget> forTemplate(String uriTemplate, String variableName) {
        return of(uriTemplate, variableName, CompletionReference.ResourceTemplate::new);
    }

    CompletionRequest withCurrentValue(String currentValue) {
        return new CompletionRequest(reference, argumentName, currentValue);
    }

    String resultTitle() {
        return "Values for " + PrintableText.of(argumentName);
    }

    private static Optional<CompletionTarget> of(String owner, String argumentName,
                                                 Function<String, CompletionReference> reference) {
        if (isBlank(owner) || isBlank(argumentName)) {
            return Optional.empty();
        }
        return Optional.of(new CompletionTarget(reference.apply(owner), argumentName));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
