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
    private final PassiveLiveRunner passiveRunner;

    public McpExchangeLog(PassiveLiveRunner passiveRunner) {
        this.passiveRunner = passiveRunner;
    }

    @Override
    public void observe(ObservedMessage message) {
        if (message.direction() == Direction.CLIENT_TO_SERVER) {
            recordRequest(message);
            return;
        }
        recordResponse(message);
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

    private void recordResponse(ObservedMessage message) {
        McpExchange exchange = new McpExchange(
                message.sessionId(),
                message.transport(),
                Direction.SERVER_TO_CLIENT,
                message.jsonrpcId(),
                FIRST_GENERATION,
                message.method(),
                // response rows carry no originating request — they correlate to their request row via the shared LinkKey, which holds the HttpRequest.
                null,
                message.parsed(),
                message.status(),
                Instant.now(),
                ExposureSurface.LIVE_RUNTIME_OUTPUT);
        exchanges.add(exchange);
        // Correlation is by the shared LinkKey: the appended row already links to its request row
        // (both share an equal LinkKey). Evicting the answered pending request both records the
        // correlation as resolved and bounds the map's growth. A response with no pending match is a
        // standalone server-initiated row — leave pendingRequests untouched.
        pendingRequests.remove(exchange.link());
        // Passive scanning targets live runtime output, so it fires only on response rows (here),
        // never on the request path.
        passiveRunner.scan(exchange);
    }

    public List<McpExchange> exchanges() {
        return List.copyOf(exchanges);
    }
}
