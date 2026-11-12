package com.mcpscanner.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PersistedJsonDocument<T> {

    private final PersistedJsonReader<T> reader;
    private final Consumer<String> store;
    private final Consumer<String> backup;
    private final McpEventLog eventLog;

    PersistedJsonDocument(String label, TypeReference<T> type, Supplier<String> stored, Consumer<String> store,
                          Consumer<String> backup, McpEventLog eventLog) {
        this.reader = new PersistedJsonReader<>(label, type, stored, eventLog);
        this.store = store;
        this.backup = backup;
        this.eventLog = eventLog;
    }

    Optional<T> read() {
        return reader.read();
    }

    void write(T value) {
        backUpUnreadableDocument();
        try {
            store.accept(McpObjectMapper.INSTANCE.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize the " + reader.label(), e);
        }
    }

    private void backUpUnreadableDocument() {
        String json = reader.storedJson();
        if (json == null || json.isBlank() || reader.isReadable(json)) {
            return;
        }
        backup.accept(json);
        eventLog.warn("Kept the unreadable persisted " + reader.label() + " in a backup before saving");
    }
}
