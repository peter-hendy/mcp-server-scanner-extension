package com.mcpscanner.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.core.protocol.McpRequests;

import java.util.Map;

final class ModernRequestAdapter {

    private ModernRequestAdapter() {}

    static McpRequests.Request adapt(String body, McpOperationFraming.Framer framer) {
        try {
            JsonNode envelope = McpObjectMapper.INSTANCE.readTree(body);
            if (!envelope.isObject() || !envelope.path("method").isTextual()
                    || !envelope.path("params").isObject()) {
                return new McpRequests.Request(body, Map.of());
            }
            String method = envelope.path("method").asText();
            ObjectNode params = (ObjectNode) envelope.path("params");
            McpRequests.Request framed;
            if (!envelope.has("id")) {
                framed = McpOperationFraming.notification(method, params, framer);
            } else if (envelope.path("id").canConvertToLong()) {
                framed = McpOperationFraming.operation(method, params, envelope.path("id").asLong(), framer);
            } else {
                return new McpRequests.Request(body, Map.of());
            }
            return McpOperationFraming.carriesProtocolEnvelope(params)
                    ? new McpRequests.Request(body, framed.headers()) : framed;
        } catch (Exception invalidProbe) {
            return new McpRequests.Request(body, Map.of());
        }
    }
}
