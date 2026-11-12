package com.mcpscanner.proxy;

import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

final class ProvenanceGuard {

    private static final long NO_SESSION = Long.MIN_VALUE;

    private final McpScannerSession scannerSession;
    private final AuditProvenance auditProvenance = new AuditProvenance();
    private long provenanceSessionGeneration = NO_SESSION;

    ProvenanceGuard(McpScannerSession scannerSession) {
        this.scannerSession = scannerSession;
    }

    Map<String, String> auditHeaders(Map<String, String> baseline) {
        synchronized (scannerSession) {
            synchronizeProvenanceSession();
            Map<String, String> wireHeaders = new HashMap<>(baseline);
            wireHeaders.keySet().removeIf("Authorization"::equalsIgnoreCase);
            String token = auditProvenance.issue(wireHeaders, scannerSession.credentialHeaderNames());
            return Map.of(ScannerSentinels.AUDIT_CAPABILITY_HEADER, token);
        }
    }

    ProxyConnection capture() {
        synchronized (scannerSession) {
            synchronizeProvenanceSession();
            return new ProxyConnection(auditProvenance.generation(), scannerSession.connectionGeneration(),
                    scannerSession.resolvedEndpoint(), scannerSession.transportType(), scannerSession.sseUrl());
        }
    }

    AuditRequest classify(Map<String, String> headers, ProxyConnection connection) {
        synchronized (scannerSession) {
            requireGeneration(connection);
            AuditProvenance.Status status = auditProvenance.classify(headers);
            return new AuditRequest(status == AuditProvenance.Status.UNMARKED ? AuditProvenance.Status.MANUAL : status,
                    connection);
        }
    }

    <T> T use(ProxyConnection connection, Supplier<T> operation) {
        synchronized (scannerSession) {
            requireCurrent(connection);
            T result = operation.get();
            requireCurrent(connection);
            return result;
        }
    }

    void requireLive(ProxyConnection connection) {
        use(connection, () -> null);
    }

    void reset() {
        auditProvenance.reset();
    }

    private void requireCurrent(ProxyConnection connection) {
        requireGeneration(connection);
        if (connection.sessionGeneration() != scannerSession.connectionGeneration()) throw new ExpiredAuditException();
    }

    private void requireGeneration(ProxyConnection connection) {
        if (connection.generation() != auditProvenance.generation()) throw new ExpiredAuditException();
    }

    private void synchronizeProvenanceSession() {
        long current = scannerSession.connectionGeneration();
        if (provenanceSessionGeneration == current) return;
        if (provenanceSessionGeneration != NO_SESSION) auditProvenance.reset();
        provenanceSessionGeneration = current;
    }
}
