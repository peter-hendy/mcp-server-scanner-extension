package com.mcpscanner.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;

final class JsonErrorBody {

    private JsonErrorBody() {}

    static String of(String message) {
        try {
            return McpObjectMapper.INSTANCE.writeValueAsString(Map.of("error", Objects.requireNonNullElse(message, "error")));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
