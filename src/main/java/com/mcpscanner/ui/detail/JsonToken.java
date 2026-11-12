package com.mcpscanner.ui.detail;

record JsonToken(int start, int end, JsonTokenKind kind) {

    int length() {
        return end - start;
    }

    String text(String source) {
        return source.substring(start, end);
    }
}
