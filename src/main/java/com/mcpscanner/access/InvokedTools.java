package com.mcpscanner.access;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.mcp.McpToolDefinition;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class InvokedTools {

    private final Function<String, Optional<McpToolDefinition>> catalog;

    public InvokedTools(Function<String, Optional<McpToolDefinition>> catalog) {
        this.catalog = catalog;
    }

    public List<McpToolDefinition> in(List<HttpRequestResponse> requests) {
        return requests.stream()
                .flatMap(request -> AccessItemKey.fromRequestBody(bodyOf(request)).stream())
                .filter(key -> key.kind() == AccessItemKey.Kind.TOOL)
                .map(AccessItemKey::name)
                .distinct()
                .map(this::definitionOf)
                .toList();
    }

    private McpToolDefinition definitionOf(String name) {
        return catalog.apply(name).orElseGet(() -> new McpToolDefinition(name, "", "{}"));
    }

    private static String bodyOf(HttpRequestResponse request) {
        return request.request() == null ? null : request.request().bodyToString();
    }
}
