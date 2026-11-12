package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.McpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ProtocolVariant(Optional<String> header, boolean captured) {

    public static final String LEGACY_VERSION = "2024-11-05";
    public static final String ABSENT_HEADER_HANDSHAKE_VERSION = "2025-03-26";

    public static ProtocolVariant asCaptured() {
        return new ProtocolVariant(Optional.empty(), true);
    }

    public static List<ProtocolVariant> downgradesFor(OperationSeed seed) {
        List<ProtocolVariant> variants = new ArrayList<>();
        Optional<String> captured = seed.protocolVersion();
        if (!captured.equals(Optional.of(LEGACY_VERSION))) {
            variants.add(new ProtocolVariant(Optional.of(LEGACY_VERSION), false));
        }
        if (captured.isPresent()) {
            variants.add(new ProtocolVariant(Optional.empty(), false));
        }
        return List.copyOf(variants);
    }

    public HttpRequest apply(HttpRequest request) {
        if (captured) {
            return request;
        }
        return header.map(version -> request.withHeader(McpHeaders.PROTOCOL_VERSION, version))
                .orElseGet(() -> request.withRemovedHeader(McpHeaders.PROTOCOL_VERSION));
    }

    public String retryNote() {
        if (captured) {
            return "";
        }
        return header.map(version -> " (sent at MCP-Protocol-Version " + version)
                .orElse(" (sent without MCP-Protocol-Version") + " after a denial at the captured version)";
    }

    public String label() {
        return header.orElse("absent");
    }
}
