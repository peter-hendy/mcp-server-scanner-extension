package com.mcpscanner.client;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.logging.McpEventLog;

import java.net.URI;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class ConnectionPreconditions {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final BooleanSupplier sseProxyAvailable;
    private final McpEventLog eventLog;

    ConnectionPreconditions(BooleanSupplier sseProxyAvailable, McpEventLog eventLog) {
        this.sseProxyAvailable = sseProxyAvailable != null ? sseProxyAvailable : () -> true;
        this.eventLog = eventLog;
    }

    boolean sseProxyAvailable() {
        return sseProxyAvailable.getAsBoolean();
    }

    void rejectSseWhenProxyUnavailable(TransportType transport) {
        if (transport != TransportType.SSE || sseProxyAvailable()) {
            return;
        }
        eventLog.error("SSE transport unavailable: the local SSE proxy failed to start. "
                + "Use the Streamable HTTP transport, which is unaffected.");
        throw new IllegalStateException(
                "SSE proxy unavailable — the local SSE proxy failed to start, so the SSE transport "
                        + "cannot be used. Streamable HTTP still works.");
    }

    void rejectPlainHttpWithAuth(String endpointUrl, AuthStrategy auth) {
        if (AuthHeaders.of(auth).isEmpty()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(endpointUrl);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) || isLoopback(uri.getHost())) {
            return;
        }
        throw new IllegalStateException(
                "Refusing to send authentication credentials over cleartext http:// to " + uri.getHost()
                        + " — use https:// or connect to a loopback host");
    }

    private static boolean isLoopback(String host) {
        return host != null && LOOPBACK_HOSTS.contains(host.toLowerCase());
    }
}
