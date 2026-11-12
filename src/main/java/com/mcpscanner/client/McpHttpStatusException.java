package com.mcpscanner.client;

public final class McpHttpStatusException extends IllegalStateException {

    private final int status;

    public McpHttpStatusException(String request, int status) {
        super(request + " failed: HTTP " + status);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
