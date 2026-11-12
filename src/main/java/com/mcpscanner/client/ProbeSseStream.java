package com.mcpscanner.client;

import com.mcpscanner.mcp.SseProbeStream;

import java.net.URI;

public final class ProbeSseStream implements SseProbeStream {

    private final URI messageUrl;
    private final SseReplyReader reader;

    ProbeSseStream(URI messageUrl, SseReplyReader reader) {
        this.messageUrl = messageUrl;
        this.reader = reader;
    }

    @Override
    public String messageUrl() {
        return messageUrl.toString();
    }

    @Override
    public String nextEvent() {
        return reader.nextEvent();
    }

    @Override
    public void beginReplyWait() {
        reader.beginReplyWait();
    }

    @Override
    public void close() {
        reader.close();
    }
}
