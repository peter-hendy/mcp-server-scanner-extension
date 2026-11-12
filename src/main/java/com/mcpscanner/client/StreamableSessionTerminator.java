package com.mcpscanner.client;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.connection.ConnectionAuthException;
import com.mcpscanner.core.connection.CredentialProvider;

import java.net.URI;
import java.util.Map;

final class StreamableSessionTerminator {

    private static final int SESSION_ID_PREVIEW_LENGTH = 6;

    private final ProtocolHttpExchange exchange;
    private final McpEventLog eventLog;

    StreamableSessionTerminator(ProtocolHttpExchange exchange, McpEventLog eventLog) {
        this.exchange = exchange;
        this.eventLog = eventLog;
    }

    void terminate(URI target, String sessionId, Map<String, String> baseHeaders, CredentialProvider credentials) {
        Map<String, String> headers;
        try {
            headers = ScannerHeaderState.withCredentials(baseHeaders, credentials);
        } catch (ConnectionAuthException refusal) {
            return;
        }
        delete(target, sessionId, headers);
    }

    private void delete(URI target, String sessionId, Map<String, String> headers) {
        try {
            exchange.delete(target, headers);
            eventLog.info("Session DELETE sent: " + preview(sessionId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            eventLog.warn("Session DELETE interrupted: " + preview(sessionId));
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            eventLog.warn("Session DELETE failed: " + reason);
        }
    }

    private static String preview(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return "<empty>";
        }
        return sessionId.substring(0, Math.min(SESSION_ID_PREVIEW_LENGTH, sessionId.length())) + "…";
    }
}
