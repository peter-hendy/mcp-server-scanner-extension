package com.mcpscanner.core.protocol;

final class JsonWriter {
    private final StringBuilder output = new StringBuilder();
    private final int maxBytes;

    private JsonWriter(int maxBytes) { this.maxBytes = maxBytes; }

    static String write(Object value, int maxBytes) {
        JsonWriter writer = new JsonWriter(maxBytes);
        writer.value(value, 0);
        String result = writer.output.toString();
        if (result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
            throw new IllegalArgumentException("JSON exceeds limit");
        return result;
    }

    private void value(Object value, int depth) {
        if (depth > 32) throw new IllegalArgumentException("JSON nesting limit");
        if (value == null) append("null");
        else if (value instanceof String text) string(text);
        else if (value instanceof Boolean || value instanceof java.math.BigDecimal || value instanceof Long)
            append(value.toString());
        else if (value instanceof java.util.Map<?, ?> map) object(map, depth + 1);
        else if (value instanceof java.util.List<?> list) array(list, depth + 1);
        else throw new IllegalArgumentException("unsupported JSON value");
    }

    private void object(java.util.Map<?, ?> map, int depth) {
        append("{");
        boolean first = true;
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("object key required");
            if (!first) append(",");
            first = false;
            string(key);
            append(":");
            value(entry.getValue(), depth);
        }
        append("}");
    }

    private void array(java.util.List<?> list, int depth) {
        append("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) append(",");
            first = false;
            value(item, depth);
        }
        append("]");
    }

    private void string(String text) {
        append("\"");
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '"' || character == '\\') append("\\" + character);
            else if (character < 32 || Character.isSurrogate(character))
                append(String.format("\\u%04x", (int) character));
            else append(String.valueOf(character));
        }
        append("\"");
    }

    private void append(String text) {
        if (text.length() > maxBytes - output.length()) throw new IllegalArgumentException("JSON exceeds limit");
        output.append(text);
    }
}
