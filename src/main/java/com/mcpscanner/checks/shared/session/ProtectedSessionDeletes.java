package com.mcpscanner.checks.shared.session;

import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.core.connection.SessionDeletePort;
import com.mcpscanner.core.protocol.McpFlow;

import java.util.Map;

final class ProtectedSessionDeletes implements SessionDeletePort {

    private final SessionDeletePort delegate;
    private final String protectedSessionId;

    ProtectedSessionDeletes(SessionDeletePort delegate, String protectedSessionId) {
        this.delegate = delegate;
        this.protectedSessionId = protectedSessionId;
    }

    @Override
    public McpFlow.HttpResponse delete(Map<String, String> headers) {
        return targetsProtectedSession(headers) ? null : delegate.delete(headers);
    }

    private boolean targetsProtectedSession(Map<String, String> headers) {
        return headers.entrySet().stream()
                .filter(header -> McpHeaders.SESSION_ID.equalsIgnoreCase(header.getKey()))
                .anyMatch(header -> protectedSessionId.equals(header.getValue()));
    }
}
