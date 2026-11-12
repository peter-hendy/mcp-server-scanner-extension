package com.mcpscanner.client;

import java.io.IOException;
import java.net.URI;

public final class ConnectionClosedBeforeReplyException extends IOException {

    public ConnectionClosedBeforeReplyException(URI target) {
        super("Burp returned an empty reply from " + target + ": the connection closed before any reply byte");
    }
}
