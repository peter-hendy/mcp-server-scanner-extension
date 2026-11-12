package com.mcpscanner.proxy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.util.Optional;

record TopLevelId(int start, int end, String text) {

    private static final String ID_FIELD = "id";

    static Optional<TopLevelId> in(String json) {
        if (json == null || json.isEmpty()) {
            return Optional.empty();
        }
        try (JsonParser parser = McpObjectMapper.INSTANCE.getFactory().createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return Optional.empty();
            }
            return idOf(parser, json);
        } catch (IOException unparsable) {
            return Optional.empty();
        }
    }

    String spliceInto(String json, TopLevelId replacement) {
        return json.substring(0, start) + replacement.text() + json.substring(end);
    }

    private static Optional<TopLevelId> idOf(JsonParser parser, String json) throws IOException {
        Optional<TopLevelId> lastId = Optional.empty();
        for (JsonToken token = parser.nextToken(); token == JsonToken.FIELD_NAME; token = parser.nextToken()) {
            boolean isId = ID_FIELD.equals(parser.currentName());
            JsonToken value = parser.nextToken();
            if (isId) {
                lastId = isValidId(value) ? Optional.of(spanOf(parser, json)) : Optional.empty();
            } else {
                parser.skipChildren();
            }
        }
        return lastId;
    }

    private static boolean isValidId(JsonToken value) {
        return value == JsonToken.VALUE_STRING || value == JsonToken.VALUE_NUMBER_INT
                || value == JsonToken.VALUE_NUMBER_FLOAT;
    }

    private static TopLevelId spanOf(JsonParser parser, String json) throws IOException {
        int start = (int) parser.currentTokenLocation().getCharOffset();
        parser.getText();
        int end = (int) parser.currentLocation().getCharOffset();
        return new TopLevelId(start, end, json.substring(start, end));
    }
}
