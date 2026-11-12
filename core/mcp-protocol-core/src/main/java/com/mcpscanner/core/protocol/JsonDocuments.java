package com.mcpscanner.core.protocol;

public final class JsonDocuments {
    private JsonDocuments() {}
    public static Object parse(String source, int maxBytes) { return JsonCodec.parse(source, maxBytes); }
}
