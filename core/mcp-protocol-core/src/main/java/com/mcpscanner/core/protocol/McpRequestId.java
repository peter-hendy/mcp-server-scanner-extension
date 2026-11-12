package com.mcpscanner.core.protocol;

public record McpRequestId(Object value) {
    private static final int MAX_SCALARS = 4096;
    private static final int MAX_JSON_BYTES = MAX_SCALARS * 12 + 2;
    public McpRequestId {
        if (value instanceof java.math.BigDecimal number) {
            try { value = number.longValueExact(); }
            catch (ArithmeticException invalid) { throw new IllegalArgumentException("signed 64-bit integer ID required", invalid); }
        }
        if (!(value instanceof Long) && !(value instanceof String text && validText(text)))
            throw new IllegalArgumentException("bounded string or signed 64-bit integer ID required");
    }

    private static boolean validText(String text) {
        if (text.codePointCount(0, text.length()) > MAX_SCALARS) return false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= text.length() || !Character.isLowSurrogate(text.charAt(index))) return false;
            } else if (Character.isLowSurrogate(current)) return false;
        }
        return true;
    }

    public static McpRequestId of(long value) { return new McpRequestId(value); }
    public static McpRequestId of(Object value) { return new McpRequestId(value); }
    public static McpRequestId parse(String json) { return of(JsonCodec.parse(json, 65536)); }
    public String json() { return JsonWriter.write(value, MAX_JSON_BYTES); }
}
