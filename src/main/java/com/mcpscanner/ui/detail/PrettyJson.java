package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.UncheckedIOException;

final class PrettyJson {

    private static final String INDENT = "  ";
    private static final String NEWLINE = "\n";

    private final ObjectWriter writer = McpObjectMapper.INSTANCE.writer()
            .without(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .with(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter(INDENT, NEWLINE)));

    String print(JsonNode node) {
        try {
            return writer.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
