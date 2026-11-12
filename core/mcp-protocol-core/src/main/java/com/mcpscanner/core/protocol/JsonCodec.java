package com.mcpscanner.core.protocol;

final class JsonCodec {
    private final String source;
    private int position;

    private JsonCodec(String source, int maxBytes) {
        if (source == null || maxBytes <= 0 || source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
            throw new IllegalArgumentException("JSON exceeds limit");
        this.source = source;
    }

    static Object parse(String source, int maxBytes) {
        JsonCodec parser = new JsonCodec(source, maxBytes);
        Object value = parser.value(0);
        parser.space();
        if (parser.position != source.length()) throw new IllegalArgumentException("trailing JSON data");
        return value;
    }

    private Object value(int depth) {
        if (depth > 32) throw new IllegalArgumentException("JSON nesting limit");
        space();
        if (position >= source.length()) throw new IllegalArgumentException("incomplete JSON");
        char c = source.charAt(position);
        if (c == '{') return object(depth + 1);
        if (c == '[') return array(depth + 1);
        if (c == '"') return string();
        if (c == 't') return literal("true", Boolean.TRUE);
        if (c == 'f') return literal("false", Boolean.FALSE);
        if (c == 'n') return literal("null", null);
        return number();
    }

    private java.util.Map<String, Object> object(int depth) {
        position++;
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        space();
        if (take('}')) return values;
        while (true) {
            space();
            if (position >= source.length() || source.charAt(position) != '"') throw new IllegalArgumentException("object key required");
            String key = string();
            space();
            require(':');
            Object value = value(depth);
            if (values.containsKey(key)) throw new IllegalArgumentException("duplicate key");
            values.put(key, value);
            space();
            if (take('}')) return values;
            require(',');
        }
    }

    private java.util.List<Object> array(int depth) {
        position++;
        java.util.List<Object> values = new java.util.ArrayList<>();
        space();
        if (take(']')) return values;
        while (true) {
            values.add(value(depth));
            space();
            if (take(']')) return values;
            require(',');
        }
    }

    private String string() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (position < source.length()) {
            char c = source.charAt(position++);
            if (c == '"') return result.toString();
            if (c == '\\') {
                if (position >= source.length()) throw new IllegalArgumentException("incomplete escape");
                char escaped = source.charAt(position++);
                if (escaped == 'u') {
                    if (source.length() - position < 4) throw new IllegalArgumentException("incomplete unicode escape");
                    try { result.append((char) Integer.parseInt(source.substring(position, position + 4), 16)); }
                    catch (NumberFormatException invalid) { throw new IllegalArgumentException("invalid unicode escape", invalid); }
                    position += 4;
                } else {
                    int index = "\"\\/bfnrt".indexOf(escaped);
                    if (index < 0) throw new IllegalArgumentException("invalid escape");
                    result.append("\"\\/\b\f\n\r\t".charAt(index));
                }
            } else {
                if (c < 32) throw new IllegalArgumentException("control in string");
                result.append(c);
            }
        }
        throw new IllegalArgumentException("unterminated string");
    }

    private Object number() {
        int start = position;
        if (take('-') && position >= source.length()) throw new IllegalArgumentException("invalid number");
        digits(true);
        if (take('.')) digits(false);
        if (take('e') || take('E')) {
            if (!take('+')) take('-');
            digits(false);
        }
        try { return new java.math.BigDecimal(source.substring(start, position)); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("invalid number", invalid); }
    }

    private void digits(boolean integerPart) {
        int start = position;
        while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
        if (start == position) throw new IllegalArgumentException("digits required");
        if (integerPart && position - start > 1 && source.charAt(start) == '0') throw new IllegalArgumentException("leading zero");
    }

    private Object literal(String token, Object value) {
        if (!source.startsWith(token, position)) throw new IllegalArgumentException("invalid literal");
        position += token.length();
        return value;
    }

    private void space() {
        while (position < source.length() && " \t\r\n".indexOf(source.charAt(position)) >= 0) position++;
    }

    private boolean take(char c) {
        if (position >= source.length() || source.charAt(position) != c) return false;
        position++;
        return true;
    }

    private void require(char c) {
        if (!take(c)) throw new IllegalArgumentException("expected " + c);
    }
}
