package com.mcpscanner.core.protocol;

public interface ServerRequestHandler {
    java.util.Optional<McpRequests.Request> reply(McpResponses.ServerRequest request);

    static ServerRequestHandler disabled() {
        return request -> java.util.Optional.empty();
    }
}
