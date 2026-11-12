package com.mcpscanner.client;

import java.util.Map;

final class JsonRpcErrorText {

    private static final String LABEL = "JSON-RPC error";

    private JsonRpcErrorText() {
    }

    static String of(Map<String, Object> error) {
        StringBuilder text = new StringBuilder(LABEL);
        if (error.get("code") instanceof Number code) {
            text.append(' ').append(code.longValue());
        }
        if (error.get("message") instanceof String message && !message.isBlank()) {
            text.append(": ").append(message);
        }
        return text.toString();
    }
}
