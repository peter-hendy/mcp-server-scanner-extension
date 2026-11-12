package com.mcpscanner.mcp;

import com.mcpscanner.core.protocol.JsonDocuments;
import com.mcpscanner.core.protocol.McpRequestId;
import com.mcpscanner.core.protocol.McpRequests;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class InFlightServerPings {

    private final ServerPings serverPings;
    private final Consumer<McpRequests.Request> post;
    private final Map<McpRequestId, ServerPingAnswerer> pendingCalls = new LinkedHashMap<>();

    public InFlightServerPings(ServerPings serverPings, Consumer<McpRequests.Request> post) {
        this.serverPings = serverPings;
        this.post = post;
    }

    public synchronized void callStarted(McpRequests.Request request) {
        McpRequestId id = messageId(request.body());
        if (id != null) {
            pendingCalls.put(id, serverPings.answerer(request.headers(), post));
        }
    }

    public synchronized void callEnded(McpRequests.Request request) {
        forget(messageId(request.body()));
    }

    public boolean consume(String data) {
        if (answererWithBudget().consume(data)) {
            return true;
        }
        endCallAnsweredBy(data);
        return false;
    }

    private synchronized ServerPingAnswerer answererWithBudget() {
        return pendingCalls.values().stream().filter(ServerPingAnswerer::hasBudget).findFirst()
                .orElse(ServerPingAnswerer.ignoring());
    }

    private synchronized void endCallAnsweredBy(String data) {
        forget(messageId(data));
    }

    private void forget(McpRequestId id) {
        if (id != null) {
            pendingCalls.remove(id);
        }
    }

    private static McpRequestId messageId(String json) {
        try {
            return JsonDocuments.parse(json, BoundedBodyReader.MAX_RESPONSE_BYTES) instanceof Map<?, ?> message
                    && message.get("id") != null ? McpRequestId.of(message.get("id")) : null;
        } catch (IllegalArgumentException notAnIdentifiedMessage) {
            return null;
        }
    }
}
