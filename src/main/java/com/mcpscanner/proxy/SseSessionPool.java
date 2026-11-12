package com.mcpscanner.proxy;

import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.config.RedirectMode;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class SseSessionPool {

    record Lease(SseScanSession session, Map<String, String> headers) {}

    private final McpScannerSession scannerSession;
    private final ProvenanceGuard guard;
    private final SseScanSessionFactory managedSessionFactory;
    private final SseSessionContext context;
    private final Set<SseScanSession> isolatedSessions = new HashSet<>();
    private SseScanSession managed;
    private Map<String, String> managedHeaders;
    private RedirectMode managedRedirectMode;
    private boolean stopped;

    SseSessionPool(McpScannerSession scannerSession, ProvenanceGuard guard,
                   SseScanSessionFactory managedSessionFactory, SseSessionContext context) {
        this.scannerSession = scannerSession;
        this.guard = guard;
        this.managedSessionFactory = managedSessionFactory;
        this.context = context;
    }

    synchronized Lease managed(ProxyConnection connection) {
        requireRunning();
        Map<String, String> headers = guard.use(connection, () -> Map.copyOf(scannerSession.scannerHeaders()));
        RedirectMode currentMode = context.redirects().mode();
        if (managed != null && isStale(headers, currentMode)) {
            closeManaged();
        }
        if (managed == null) {
            managed = managedSessionFactory.create(connection.sseUrl(), headers, context);
        }
        managedHeaders = headers;
        managedRedirectMode = currentMode;
        return new Lease(managed, headers);
    }

    ProxyResponse forwardIsolated(ProxyConnection connection, Map<String, String> connectionHeaders,
                                  String body, Map<String, String> headers) throws IOException, InterruptedException {
        SseScanSession isolated = openIsolated(connection, connectionHeaders);
        try (isolated) {
            return isolated.forwardRequest(body, headers);
        } finally {
            releaseIsolated(isolated);
        }
    }

    synchronized void retire(SseScanSession expected) {
        if (managed == expected) closeManaged();
    }

    synchronized void closeAll() {
        closeManaged();
        isolatedSessions.forEach(this::close);
        isolatedSessions.clear();
    }

    synchronized void stop() {
        stopped = true;
        closeAll();
    }

    private synchronized SseScanSession openIsolated(ProxyConnection connection, Map<String, String> headers) {
        requireRunning();
        guard.requireLive(connection);
        SseScanSession isolated = new SseScanSession(connection.sseUrl(), headers, context);
        isolatedSessions.add(isolated);
        return isolated;
    }

    private synchronized void releaseIsolated(SseScanSession isolated) {
        isolatedSessions.remove(isolated);
    }

    private boolean isStale(Map<String, String> headers, RedirectMode currentMode) {
        return (managedHeaders != null && !managedHeaders.equals(headers))
                || (managedRedirectMode != null && managedRedirectMode != currentMode);
    }

    private void requireRunning() {
        if (stopped) throw new IllegalStateException("Proxy stopped");
    }

    private void closeManaged() {
        if (managed != null) {
            close(managed);
            managed = null;
            managedHeaders = null;
            managedRedirectMode = null;
        }
    }

    private void close(SseScanSession session) {
        try {
            session.close();
        } catch (IOException e) {
            scannerSession.eventLog().warn("Error closing SSE scan session: " + e.getMessage());
        }
    }
}
