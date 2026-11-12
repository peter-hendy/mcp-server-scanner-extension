package com.mcpscanner.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.Optional;
import java.util.function.Supplier;

final class PersistedJsonReader<T> {

    private final String label;
    private final ObjectReader reader;
    private final Supplier<String> stored;
    private final McpEventLog eventLog;

    PersistedJsonReader(String label, TypeReference<T> type, Supplier<String> stored, McpEventLog eventLog) {
        this.label = label;
        this.reader = McpObjectMapper.INSTANCE.readerFor(type)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.stored = stored;
        this.eventLog = eventLog;
    }

    String label() {
        return label;
    }

    String storedJson() {
        return stored.get();
    }

    Optional<T> read() {
        String json = stored.get();
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(reader.readValue(json));
        } catch (JsonProcessingException | RuntimeException e) {
            eventLog.warn("Ignoring corrupt persisted " + label + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    boolean isReadable(String json) {
        try {
            reader.readValue(json);
            return true;
        } catch (JsonProcessingException | RuntimeException e) {
            return false;
        }
    }
}
