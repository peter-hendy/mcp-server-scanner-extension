package com.mcpscanner.ui.completion;

enum CompletionAvailability {
    AVAILABLE(null),
    NOT_ADVERTISED("The server does not advertise completions"),
    DISCONNECTED("Connect to an MCP server first"),
    MALFORMED_ITEM("This item has no usable name, so the server cannot be asked");

    private final String unavailableReason;

    CompletionAvailability(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }

    boolean isAvailable() {
        return this == AVAILABLE;
    }

    String unavailableReason() {
        return unavailableReason;
    }
}
