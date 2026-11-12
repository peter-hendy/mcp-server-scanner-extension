package com.mcpscanner.mcp;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.Set;

final class JsonRpcRequestClassifier {

    private static final Set<String> MCP_METHOD_PREFIXES = Set.of(
            "tools/", "resources/", "prompts/", "sampling/", "roots/", "completion/",
            "logging/", "notifications/");
    private static final Set<String> MCP_EXACT_METHODS =
            Set.of(McpMethods.INITIALIZE, McpMethods.SERVER_DISCOVER, McpMethods.PING);

    private JsonRpcRequestClassifier() {}

    static McpRequestKind classify(HttpRequestResponse requestResponse) {
        return postBody(requestResponse.request())
                .flatMap(JsonRpcRequestClassifier::looseJsonRpcMethod)
                .map(JsonRpcRequestClassifier::kindOf)
                .orElse(McpRequestKind.NOT_MCP);
    }

    private static McpRequestKind kindOf(String method) {
        return switch (method) {
            case McpMethods.TOOLS_CALL -> McpRequestKind.TOOLS_CALL;
            case McpMethods.TOOLS_LIST -> McpRequestKind.TOOLS_LIST;
            case McpMethods.RESOURCES_READ -> McpRequestKind.RESOURCES_READ;
            case McpMethods.PROMPTS_GET -> McpRequestKind.PROMPTS_GET;
            default -> isMcpMethod(method) ? McpRequestKind.OTHER_MCP : McpRequestKind.NOT_MCP;
        };
    }

    private static boolean isMcpMethod(String method) {
        return MCP_EXACT_METHODS.contains(method)
                || MCP_METHOD_PREFIXES.stream().anyMatch(method::startsWith);
    }

    private static Optional<String> looseJsonRpcMethod(String body) {
        return parse(body)
                .filter(root -> root.path("jsonrpc").isTextual())
                .flatMap(JsonRpcRequestClassifier::textualMethod);
    }

    private static Optional<String> textualMethod(JsonNode root) {
        JsonNode method = root.path("method");
        return method.isTextual() ? Optional.of(method.asText()) : Optional.empty();
    }

    private static Optional<JsonNode> parse(String body) {
        try {
            return Optional.ofNullable(McpObjectMapper.INSTANCE.readTree(body));
        } catch (Exception unparseable) {
            return Optional.empty();
        }
    }

    private static Optional<String> postBody(HttpRequest request) {
        if (!"POST".equalsIgnoreCase(request.method())) {
            return Optional.empty();
        }
        String body = request.bodyToString();
        return body == null || body.isEmpty() ? Optional.empty() : Optional.of(body);
    }
}
