package com.mcpscanner.checks.catalog.jwtvalidation;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.mcp.McpRequestKind;

import java.util.Set;

final class ReplayableCall {

    private static final Set<McpRequestKind> OPERATIONS =
            Set.of(McpRequestKind.TOOLS_CALL, McpRequestKind.RESOURCES_READ, McpRequestKind.PROMPTS_GET);

    private ReplayableCall() {}

    static boolean isReplayable(HttpRequestResponse exchange) {
        return exchange.request() != null && "POST".equalsIgnoreCase(exchange.request().method())
                && OPERATIONS.contains(McpRequestDetector.classify(exchange))
                && JsonRpcEnvelope.of(exchange.request()).hasScalarId();
    }
}
