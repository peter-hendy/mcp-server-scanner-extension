package com.mcpscanner.checks.catalog.filetraversal;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ReplyTexts {

    private static final int MAX_NESTED_JSON_DEPTH = 2;
    private static final String BLOB_FIELD = "blob";

    private final List<String> texts = new ArrayList<>();

    private ReplyTexts() {}

    static List<String> collect(List<JsonNode> roots) {
        ReplyTexts collector = new ReplyTexts();
        roots.forEach(root -> collector.walk(root, null, 0));
        return List.copyOf(collector.texts);
    }

    private void walk(JsonNode node, String fieldName, int jsonDepth) {
        if (node.isTextual()) {
            addText(node.asText(), fieldName, jsonDepth);
        } else if (node.isObject()) {
            node.fields().forEachRemaining(field -> walkField(field, jsonDepth));
        } else if (node.isArray()) {
            node.forEach(element -> walk(element, fieldName, jsonDepth));
        }
    }

    private void walkField(Map.Entry<String, JsonNode> field, int jsonDepth) {
        walk(field.getValue(), field.getKey(), jsonDepth);
    }

    private void addText(String text, String fieldName, int jsonDepth) {
        texts.add(text);
        if (BLOB_FIELD.equals(fieldName)) {
            decodedBlob(text).ifPresent(texts::add);
        }
        if (jsonDepth < MAX_NESTED_JSON_DEPTH && looksLikeJson(text)) {
            nestedJson(text).ifPresent(nested -> walk(nested, null, jsonDepth + 1));
        }
    }

    private static boolean looksLikeJson(String text) {
        String trimmed = text.strip();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static Optional<JsonNode> nestedJson(String text) {
        try {
            return Optional.ofNullable(McpObjectMapper.INSTANCE.readTree(text));
        } catch (Exception notJson) {
            return Optional.empty();
        }
    }

    private static Optional<String> decodedBlob(String blob) {
        try {
            return Optional.of(new String(Base64.getMimeDecoder().decode(blob), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException notBase64) {
            return Optional.empty();
        }
    }
}
