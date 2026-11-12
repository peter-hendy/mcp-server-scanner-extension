package com.mcpscanner.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CompletionRequest(CompletionReference reference, String argumentName, String currentValue) {

    public static final String METHOD = McpMethods.COMPLETION_COMPLETE;

    public CompletionRequest {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(argumentName, "argumentName");
        currentValue = currentValue == null ? "" : currentValue;
    }

    public String paramsJson() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ref", reference.toRef());
        params.put("argument", Map.of("name", argumentName, "value", currentValue));
        try {
            return McpObjectMapper.INSTANCE.writeValueAsString(params);
        } catch (JsonProcessingException unreachable) {
            throw new IllegalStateException("Completion params are not serialisable", unreachable);
        }
    }
}
