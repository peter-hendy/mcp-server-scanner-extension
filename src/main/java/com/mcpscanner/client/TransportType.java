package com.mcpscanner.client;

public enum TransportType {
    AUTO("Auto"),
    MODERN_HTTP("HTTP 2026-07-28"),
    STREAMABLE_HTTP("HTTP 2025 (session)"),
    SSE("SSE 2024-11-05");

    private final String displayName;

    TransportType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
