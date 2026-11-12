package com.mcpscanner.proxy;

import com.mcpscanner.mcp.McpRequestDetector;

import java.util.Map;
import java.util.Objects;

final class ConnectAsToolCallTap {

    private final ToolCallReplyObserver observer;

    ConnectAsToolCallTap(ToolCallReplyObserver observer) {
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    void observe(String requestBody, Map<String, String> headers, AuditRequest audit, ProxyResponse reply) {
        if (audit.isManaged() && sentAsConnectAsUser(headers) && McpRequestDetector.isToolCallBody(requestBody)) {
            observer.replied(reply.statusCode(), Objects.requireNonNullElse(reply.body(), ""));
        }
    }

    private static boolean sentAsConnectAsUser(Map<String, String> headers) {
        return !SentinelHeaders.hasIdentity(headers) && !SentinelHeaders.hasStripAuth(headers);
    }
}
