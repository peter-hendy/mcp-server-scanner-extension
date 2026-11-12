package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequestId;
import com.mcpscanner.core.protocol.McpRequests;
import com.mcpscanner.core.protocol.McpResponses;
import java.util.Optional;

public final class SseProbeSession implements AutoCloseable {
    private final SseSession sse;
    private final McpFlow.Session session;

    SseProbeSession(SseSession sse) {
        this.sse = sse;
        this.session = sse.session();
    }

    public McpFlow.Session session() { return session; }

    public Optional<String> sessionId() { return SseSessionIds.from(session.endpoint()); }

    public McpResponses.GenericReply call(McpRequests.Request request, McpRequestId expectedId) {
        return sse.call(request, expectedId);
    }

    public McpFlow.HttpResponse sendNotification(McpRequests.Request notification) {
        return sse.sendNotification(notification);
    }

    public void terminate() { sse.close(); }

    @Override public void close() { terminate(); }
}
