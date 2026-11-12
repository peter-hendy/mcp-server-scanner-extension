package com.mcpscanner.engine;

import java.util.Objects;
import java.util.concurrent.CompletionException;

final class FailureDescription {

    private FailureDescription() {
    }

    static String of(Throwable failure) {
        Throwable cause = unwrapped(failure);
        return Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName());
    }

    private static Throwable unwrapped(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
