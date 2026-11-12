package com.mcpscanner.core.protocol;

public final class PingResponder implements ServerRequestHandler {
    private static final String PING = "ping";
    private static final String EMPTY_RESULT = "{}";

    private final McpFlow.Session session;

    public PingResponder(McpFlow.Session session) {
        this.session = java.util.Objects.requireNonNull(session, "session required");
    }

    @Override
    public java.util.Optional<McpRequests.Request> reply(McpResponses.ServerRequest request) {
        if (!PING.equals(request.method())) return java.util.Optional.empty();
        return java.util.Optional.of(session.response(request.id(), EMPTY_RESULT));
    }
}
