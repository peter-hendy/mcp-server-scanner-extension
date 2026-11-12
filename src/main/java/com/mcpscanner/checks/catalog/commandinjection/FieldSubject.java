package com.mcpscanner.checks.catalog.commandinjection;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.mcp.McpMethods;

record FieldSubject(String url, String method, String tool, String field) {

    static FieldSubject of(HttpRequest request, String insertionPointName) {
        JsonNode envelope = JsonRpcEnvelope.of(request).root().orElseGet(MissingNode::getInstance);
        String method = envelope.path("method").asText("");
        String tool = McpMethods.TOOLS_CALL.equals(method) ? envelope.path("params").path("name").asText("") : "";
        return new FieldSubject(request.url(), method, tool, insertionPointName == null ? "" : insertionPointName);
    }

    String operation() {
        return tool.isEmpty() ? method : tool;
    }
}
