package com.mcpscanner.mcp;

import java.util.Locale;

public final class BurpStreamingRefusal {

    private static final String MARKER = "streaming response";
    private static final String BURP_PACKAGE = "burp.";

    private BurpStreamingRefusal() {}

    public static boolean refused(RuntimeException failure) {
        return namesStreamingResponse(failure) || isMessagelessBurpRefusal(failure);
    }

    private static boolean namesStreamingResponse(RuntimeException failure) {
        String message = failure.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(MARKER);
    }

    private static boolean isMessagelessBurpRefusal(RuntimeException failure) {
        Throwable cause = failure.getCause();
        return failure.getClass() == RuntimeException.class
                && cause != null
                && isBareCheckedBurpException(cause);
    }

    private static boolean isBareCheckedBurpException(Throwable cause) {
        return cause instanceof Exception
                && !(cause instanceof RuntimeException)
                && cause.getClass().getName().startsWith(BURP_PACKAGE)
                && cause.getMessage() == null
                && cause.getCause() == null;
    }
}
