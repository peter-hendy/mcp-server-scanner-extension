package com.mcpscanner.identity;

public final class FailureReason {

    private FailureReason() {
    }

    public static String of(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String outer = describe(failure);
        return root == failure ? outer : outer + ": " + describe(root);
    }

    public static String describe(Throwable failure) {
        return failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
    }
}
