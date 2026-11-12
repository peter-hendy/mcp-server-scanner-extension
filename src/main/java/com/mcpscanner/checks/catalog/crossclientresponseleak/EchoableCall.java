package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.checks.shared.jsonrpc.JsonNodes;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpOperationFraming;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class EchoableCall {

    private static final int MAX_ARGUMENTS = 3;

    private final ObjectNode params;

    private EchoableCall(ObjectNode params) {
        this.params = params;
    }

    static Optional<EchoableCall> of(HttpRequest request) {
        JsonRpcEnvelope envelope = JsonRpcEnvelope.of(request);
        if (!isToolCall(envelope)) {
            return Optional.empty();
        }
        JsonNode params = envelope.root().orElseThrow().path("params");
        if (!params.path("arguments").isObject()) {
            return Optional.empty();
        }
        EchoableCall call = new EchoableCall(McpOperationFraming.withoutProtocolEnvelope((ObjectNode) params));
        return call.stringArguments().isEmpty() ? Optional.empty() : Optional.of(call);
    }

    List<String> stringArguments() {
        JsonNode arguments = params.path("arguments");
        return JsonNodes.fields(arguments).stream()
                .filter(field -> field.getValue().isTextual())
                .map(Map.Entry::getKey)
                .limit(MAX_ARGUMENTS)
                .toList();
    }

    String paramsWith(String argument, String marker) {
        ObjectNode copy = params.deepCopy();
        ((ObjectNode) copy.get("arguments")).put(argument, marker);
        return copy.toString();
    }

    private static boolean isToolCall(JsonRpcEnvelope envelope) {
        return envelope.isJsonRpc2() && envelope.hasScalarId()
                && envelope.method().filter(McpMethods.TOOLS_CALL::equals).isPresent();
    }
}
