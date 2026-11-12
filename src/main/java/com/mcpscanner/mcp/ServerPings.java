package com.mcpscanner.mcp;

import com.mcpscanner.core.protocol.McpRequests;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ServerPings {

    private final BooleanSupplier enabled;

    public ServerPings(BooleanSupplier enabled) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    public ServerPingAnswerer answerer(Map<String, String> sentHeaders, Consumer<McpRequests.Request> post) {
        return enabled.getAsBoolean() ? ServerPingAnswerer.answering(sentHeaders, post) : ServerPingAnswerer.ignoring();
    }
}
