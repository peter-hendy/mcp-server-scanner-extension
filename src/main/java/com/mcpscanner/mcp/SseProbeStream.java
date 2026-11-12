package com.mcpscanner.mcp;

public interface SseProbeStream extends AutoCloseable {

    String messageUrl();

    String nextEvent();

    void beginReplyWait();

    @Override
    void close();
}
