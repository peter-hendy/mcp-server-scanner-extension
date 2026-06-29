package com.mcpscanner.proxy.observe.preflight;

/**
 * Honest WARN: the three streaming-response settings Montoya cannot read. Without them Burp will not
 * surface MCP's {@code text/event-stream} bodies to the proxy intact.
 */
public final class StreamingSettingsReminder extends OperatorReminder {

    public static final String LABEL = "Streaming response settings";

    public StreamingSettingsReminder() {
        super(LABEL, "In Settings -> Network -> HTTP -> Streaming responses, set "
                + "'Store streaming responses' ON, 'Treat text/event-stream responses as streaming' "
                + "ON, and 'Strip chunked encoding metadata from streaming responses' OFF. Montoya "
                + "cannot read these, so verify them by hand.");
    }
}
