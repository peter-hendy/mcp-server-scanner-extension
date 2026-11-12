package com.mcpscanner.checks.shared.net;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.oauth.HttpUris;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.mcp.McpRequestKind;

public final class WebMcpRequests {

    private WebMcpRequests() {
    }

    public static boolean isWebMcpRequest(HttpRequestResponse exchange) {
        return exchange.request() != null
                && McpRequestDetector.classify(exchange) != McpRequestKind.NOT_MCP
                && HttpUris.parse(exchange.request().url()).isPresent();
    }
}
