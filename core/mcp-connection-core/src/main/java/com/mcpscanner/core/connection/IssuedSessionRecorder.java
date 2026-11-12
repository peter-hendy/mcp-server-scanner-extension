package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequests;
import java.util.Optional;

final class IssuedSessionRecorder implements McpFlow.HttpPort {
    private final McpFlow.HttpPort port;
    private String version;
    private String issuedSessionId;

    IssuedSessionRecorder(McpFlow.HttpPort port, String preferredVersion) {
        this.port = port;
        this.version = preferredVersion;
    }

    @Override public McpFlow.HttpResponse send(McpRequests.Request request) {
        Headers.first(request.headers(), "MCP-Protocol-Version").ifPresent(sent -> version = sent);
        var response = port.send(request);
        if (response != null) Headers.first(response.headers(), "MCP-Session-Id").ifPresent(issued -> issuedSessionId = issued);
        return response;
    }

    Optional<String> issuedSessionId() { return Optional.ofNullable(issuedSessionId); }

    String version() { return version; }
}
