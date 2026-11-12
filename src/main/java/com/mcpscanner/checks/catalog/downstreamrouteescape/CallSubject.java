package com.mcpscanner.checks.catalog.downstreamrouteescape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;

final class CallSubject {

    private static final String UNKNOWN = "unknown MCP request";

    private CallSubject() {}

    static String of(String requestBody) {
        JsonNode root = JsonRpcEnvelope.of(requestBody).root().orElseGet(MissingNode::getInstance);
        JsonNode params = root.path("params");
        String target = params.path("name").asText(params.path("uri").asText(""));
        return (root.path("method").asText(UNKNOWN) + " " + target).strip();
    }
}
