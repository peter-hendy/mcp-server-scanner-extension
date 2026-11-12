package com.mcpscanner.mcp;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class JsonLeafLocator {

    private static final JsonFactory FACTORY = new JsonFactory();
    private static final String PARAMS_FIELD = "params";

    private JsonLeafLocator() {}

    public static List<JsonLeaf> paramsLeaves(byte[] body) {
        if (body == null || body.length == 0) {
            return List.of();
        }
        try (JsonParser parser = FACTORY.createParser(body)) {
            return parser.nextToken() == JsonToken.START_OBJECT ? leavesOfRootParams(parser) : List.of();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    public static List<JsonLeaf> rootLeaves(byte[] body) {
        if (body == null || body.length == 0) {
            return List.of();
        }
        try (JsonParser parser = FACTORY.createParser(body)) {
            return parser.nextToken() == JsonToken.START_OBJECT ? leavesOfRootObject(parser) : List.of();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static List<JsonLeaf> leavesOfRootObject(JsonParser parser) throws IOException {
        List<JsonLeaf> leaves = new ArrayList<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            collect(parser, parser.nextToken(), List.of(new JsonStep.Member(field)), leaves);
        }
        return leaves;
    }

    private static List<JsonLeaf> leavesOfRootParams(JsonParser parser) throws IOException {
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if (PARAMS_FIELD.equals(field)) {
                List<JsonLeaf> leaves = new ArrayList<>();
                collect(parser, valueToken, List.of(new JsonStep.Member(PARAMS_FIELD)), leaves);
                return leaves;
            }
            parser.skipChildren();
        }
        return List.of();
    }

    private static void collect(JsonParser parser, JsonToken token, List<JsonStep> path, List<JsonLeaf> leaves)
            throws IOException {
        switch (token) {
            case START_OBJECT -> collectObject(parser, path, leaves);
            case START_ARRAY -> collectArray(parser, path, leaves);
            default -> leaves.add(scalarLeaf(parser, token, path));
        }
    }

    private static void collectObject(JsonParser parser, List<JsonStep> path, List<JsonLeaf> leaves) throws IOException {
        int start = tokenStart(parser);
        boolean empty = true;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            empty = false;
            String key = parser.currentName();
            collect(parser, parser.nextToken(), child(path, new JsonStep.Member(key)), leaves);
        }
        addIfEmpty(empty, path, start, parser, JsonValueKind.OBJECT, leaves);
    }

    private static void collectArray(JsonParser parser, List<JsonStep> path, List<JsonLeaf> leaves) throws IOException {
        int start = tokenStart(parser);
        int index = 0;
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                throw new IOException("Unterminated array at " + JsonStep.pathOf(path));
            }
            collect(parser, token, child(path, new JsonStep.Item(index++)), leaves);
        }
        addIfEmpty(index == 0, path, start, parser, JsonValueKind.ARRAY, leaves);
    }

    private static void addIfEmpty(boolean empty, List<JsonStep> path, int start, JsonParser parser,
                                   JsonValueKind kind, List<JsonLeaf> leaves) {
        if (empty) {
            int end = currentEnd(parser);
            leaves.add(new JsonLeaf(path, start, end, kind, kind == JsonValueKind.OBJECT ? "{}" : "[]"));
        }
    }

    private static JsonLeaf scalarLeaf(JsonParser parser, JsonToken token, List<JsonStep> path) throws IOException {
        int start = tokenStart(parser);
        String text = parser.getText();
        return new JsonLeaf(path, start, currentEnd(parser), scalarKind(token), text);
    }

    private static List<JsonStep> child(List<JsonStep> parent, JsonStep step) {
        return Stream.concat(parent.stream(), Stream.of(step)).toList();
    }

    private static JsonValueKind scalarKind(JsonToken token) throws IOException {
        return switch (token) {
            case VALUE_STRING -> JsonValueKind.STRING;
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> JsonValueKind.NUMBER;
            case VALUE_TRUE, VALUE_FALSE -> JsonValueKind.BOOLEAN;
            case VALUE_NULL -> JsonValueKind.NULL;
            default -> throw new IOException("Unexpected JSON token " + token);
        };
    }

    private static int tokenStart(JsonParser parser) {
        return (int) parser.currentTokenLocation().getByteOffset();
    }

    private static int currentEnd(JsonParser parser) {
        return (int) parser.currentLocation().getByteOffset();
    }
}
