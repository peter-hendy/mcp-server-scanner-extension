package com.mcpscanner.proxy.observe;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Transport-agnostic domain sink that records observed MCP traffic as {@link McpExchange} rows.
 *
 * <p>Thread-safe: {@link #observe} is called concurrently from live (proxy / scanner) traffic. The
 * exchange list and the pending-request index are both lock-free concurrent collections so observes
 * never block one another and no row is lost under contention.
 */
public final class McpExchangeLog implements McpObservationSink {

    private static final int FIRST_GENERATION = 0;

    private final List<McpExchange> exchanges = new CopyOnWriteArrayList<>();
    // TODO(Task 7+): pendingRequests is never evicted — uncorrelated/notification/never-answered entries leak; add a bound or TTL.
    private final Map<LinkKey, McpExchange> pendingRequests = new ConcurrentHashMap<>();

    @Override
    public void observe(ObservedMessage message) {
        if (message.direction() == Direction.CLIENT_TO_SERVER) {
            recordRequest(message);
            return;
        }
        // TODO(Task 7): response correlation — match SERVER_TO_CLIENT against pendingRequests by LinkKey.
    }

    private void recordRequest(ObservedMessage message) {
        McpExchange exchange = new McpExchange(
                message.sessionId(),
                message.transport(),
                Direction.CLIENT_TO_SERVER,
                message.jsonrpcId(),
                FIRST_GENERATION,
                message.method(),
                message.request(),
                null,
                null,
                Instant.now(),
                ExposureSurface.LIVE_RUNTIME_OUTPUT);
        exchanges.add(exchange);
        // TODO(Task 7): same LinkKey (sessionId,jsonrpcId,generation) → last writer wins; revisit with real generation tracking.
        pendingRequests.put(exchange.link(), exchange);
    }

    public List<McpExchange> exchanges() {
        return List.copyOf(exchanges);
    }
}
