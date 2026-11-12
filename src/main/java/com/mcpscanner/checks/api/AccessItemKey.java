package com.mcpscanner.checks.api;

import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.mcp.McpMethods;

import java.util.Objects;
import java.util.Optional;

public record AccessItemKey(Kind kind, String name) {

    public enum Kind { TOOL, RESOURCE }

    public AccessItemKey {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }

    public static Optional<AccessItemKey> fromRequestBody(String body) {
        return fromEnvelope(JsonRpcEnvelope.of(body));
    }

    public static Optional<AccessItemKey> fromEnvelope(JsonRpcEnvelope envelope) {
        return envelope.method().flatMap(method -> keyFor(method, envelope));
    }

    private static Optional<AccessItemKey> keyFor(String method, JsonRpcEnvelope envelope) {
        return switch (method) {
            case McpMethods.TOOLS_CALL -> envelope.paramsName().map(name -> new AccessItemKey(Kind.TOOL, name));
            case McpMethods.RESOURCES_READ -> envelope.paramsUri().map(uri -> new AccessItemKey(Kind.RESOURCE, uri));
            default -> Optional.empty();
        };
    }
}
