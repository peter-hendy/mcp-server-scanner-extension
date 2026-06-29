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
        LinkKey link = new LinkKey(message.sessionId(), message.jsonrpcId(), FIRST_GENERATION);
        // Correlation is by the shared LinkKey: removing the answered pending request both records the
        // correlation as resolved and bounds the map's growth. A response with no pending match is a
        // standalone server-initiated row — the remove is a no-op and the row stays uncorrelated.
        McpExchange correlatedRequest = pendingRequests.remove(link);
        // A JSON-RPC response envelope ({jsonrpc,id,result}) has no method, and the response
        // ObservedMessage deliberately carries no request — so borrow both from the correlated request:
        // a response's content-kind IS its originating request's method (a tools/call response must be
        // scanned as tools/call output) and the host is that request's httpService(). Uncorrelated /
        // server-initiated rows keep the observed (null) values so the passive runner correctly skips
        // them — no originating request, no host to invent.
        boolean correlated = correlatedRequest != null;
        McpExchange exchange = new McpExchange(
                message.sessionId(),
                message.transport(),
                Direction.SERVER_TO_CLIENT,
                message.jsonrpcId(),
                FIRST_GENERATION,
                correlated ? correlatedRequest.method() : message.method(),
                correlated ? correlatedRequest.request() : message.request(),
                message.parsed(),
                message.status(),
                Instant.now(),
                ExposureSurface.LIVE_RUNTIME_OUTPUT);
        // Linked, not merged: this is a SEPARATE row sharing the correlated request's LinkKey; the
        // request row is untouched. The response row only borrows the request's method/request for scanning.
        exchanges.add(exchange);
        // Passive scanning targets live runtime output, so it fires only on response rows (here),
        // never on the request path.
        passiveRunner.scan(exchange);
    }

    public List<McpExchange> exchanges() {
        return List.copyOf(exchanges);
    }
}
