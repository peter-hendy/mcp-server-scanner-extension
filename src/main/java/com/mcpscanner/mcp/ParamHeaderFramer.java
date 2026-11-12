package com.mcpscanner.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.core.protocol.McpRequests;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ParamHeaderFramer implements McpOperationFraming.Framer {

    private final McpOperationFraming.Framer delegate;
    private final ToolParamHeaders tools;

    public ParamHeaderFramer(McpOperationFraming.Framer delegate, ToolParamHeaders tools) {
        this.delegate = delegate;
        this.tools = tools;
    }

    @Override
    public McpRequests.Request operation(String method, String paramsJson, long id) {
        McpRequests.Request framed = delegate.operation(method, paramsJson, id);
        return McpMethods.TOOLS_CALL.equals(method) && isModern(framed)
                ? withParamHeaders(framed, paramsJson) : framed;
    }

    @Override
    public McpRequests.Request notification(String method, String paramsJson) {
        return delegate.notification(method, paramsJson);
    }

    private McpRequests.Request withParamHeaders(McpRequests.Request framed, String paramsJson) {
        Map<String, String> paramHeaders = paramHeadersFor(paramsJson);
        if (paramHeaders.isEmpty()) {
            return framed;
        }
        Map<String, String> headers = new LinkedHashMap<>(framed.headers());
        headers.putAll(paramHeaders);
        return new McpRequests.Request(framed.body(), headers);
    }

    private Map<String, String> paramHeadersFor(String paramsJson) {
        try {
            JsonNode params = McpObjectMapper.INSTANCE.readTree(paramsJson);
            JsonNode name = params.path("name");
            return name.isTextual() ? tools.forTool(name.asText()).valuesFor(params.path("arguments")) : Map.of();
        } catch (JsonProcessingException unparseable) {
            return Map.of();
        }
    }

    private static boolean isModern(McpRequests.Request framed) {
        return framed.headers().entrySet().stream()
                .anyMatch(header -> McpHeaders.PROTOCOL_VERSION.equalsIgnoreCase(header.getKey())
                        && McpRequests.MODERN_VERSION.equals(header.getValue()));
    }
}
