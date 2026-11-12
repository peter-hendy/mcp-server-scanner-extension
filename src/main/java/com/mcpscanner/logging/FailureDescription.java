package com.mcpscanner.logging;

public final class FailureDescription {

    private FailureDescription() {
    }

    public static String of(Throwable failure) {
        Throwable described = unwrapMessageless(failure);
        String type = described.getClass().getSimpleName();
        String message = described.getMessage();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    private static Throwable unwrapMessageless(Throwable failure) {
        Throwable cause = failure.getCause();
        boolean wrapperOnly = cause != null && cause.toString().equals(failure.getMessage());
        return wrapperOnly ? unwrapMessageless(cause) : failure;
    }
}
