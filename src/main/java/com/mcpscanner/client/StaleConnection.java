package com.mcpscanner.client;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

final class StaleConnection {

    private static final String CLOSED_BEFORE_REPLY = "header parser received no bytes";

    private StaleConnection() {
    }

    static boolean closedBeforeReply(IOException failure) {
        return failure instanceof ConnectionClosedBeforeReplyException || jdkNoBytes(failure);
    }

    private static boolean jdkNoBytes(IOException failure) {
        return !(failure instanceof HttpTimeoutException)
                && failure.getMessage() != null
                && failure.getMessage().contains(CLOSED_BEFORE_REPLY);
    }
}
