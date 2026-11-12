package com.mcpscanner.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.Arrays;
import java.util.stream.Collectors;

final class RecordedReplyBody {
    private static final String DATA_PREFIX = "data: ";
    private static final String ID = "id";

    private RecordedReplyBody() {}

    static String answering(RecordedExchange exchange, JsonNode requestId) {
        return exchange.framedAsEventStream()
                ? retargetDataLines(exchange.body(), requestId)
                : retarget(exchange.body(), requestId);
    }

    private static String retargetDataLines(String body, JsonNode requestId) {
        return Arrays.stream(body.split("\n", -1))
                .map(line -> line.startsWith(DATA_PREFIX)
                        ? DATA_PREFIX + retarget(line.substring(DATA_PREFIX.length()), requestId)
                        : line)
                .collect(Collectors.joining("\n"));
    }

    private static String retarget(String json, JsonNode requestId) {
        try {
            JsonNode reply = McpObjectMapper.INSTANCE.readTree(json);
            if (reply instanceof ObjectNode envelope && envelope.has(ID)) {
                envelope.set(ID, requestId);
                return envelope.toString();
            }
            return json;
        } catch (JsonProcessingException notJson) {
            return json;
        }
    }
}
