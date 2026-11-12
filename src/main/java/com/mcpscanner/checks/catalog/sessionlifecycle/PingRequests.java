package com.mcpscanner.checks.catalog.sessionlifecycle;

import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.core.protocol.McpRequests;

import java.util.concurrent.atomic.AtomicLong;

final class PingRequests {

    private static final String NO_PARAMS = "{}";

    private final AtomicLong ids = new AtomicLong();

    McpRequests.Request withSession(String version, String sessionId) {
        return McpRequests.httpPost(McpMethods.PING, version, sessionId, NO_PARAMS, ids.incrementAndGet());
    }

    McpRequests.Request withoutSession(String version) {
        return withSession(version, null);
    }

    String liveBody() {
        return McpRequests.envelope(McpMethods.PING, NO_PARAMS, ids.incrementAndGet());
    }
}
