package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.concurrent.atomic.AtomicInteger;

public final class ToolsListProbe {

    private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/list\",\"params\":{}}";

    private final AtomicInteger nextId;

    public ToolsListProbe(int firstId) {
        this.nextId = new AtomicInteger(firstId);
    }

    public HttpRequest on(HttpRequest request) {
        return request.withBody(TOOLS_LIST.formatted(nextId.getAndIncrement()));
    }
}
