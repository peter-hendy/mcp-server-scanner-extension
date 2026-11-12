package com.mcpscanner.client;

import java.io.IOException;
import java.net.URI;

public final class BurpStreamingResponseException extends IOException {

    public BurpStreamingResponseException(URI target) {
        super(target + " replied with a stream (text/event-stream). Burp doesn't pass streamed replies to "
                + "extensions, so this server can't be reached while \"" + BurpNetworkingSetting.NAME + "\" is on. "
                + "Turn it off to connect.");
    }

    public static boolean caused(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof BurpStreamingResponseException) {
                return true;
            }
        }
        return false;
    }
}
