package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.ExtensionVersion;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.core.protocol.McpRequestId;
import com.mcpscanner.core.protocol.McpRequests;

public final class McpSessionHandshake {

    private static final String CLIENT_NAME = "mcp-server-scanner";

    private final String initializeId;
    private final String absentHeaderVersion;

    private McpSessionHandshake(String initializeId, String absentHeaderVersion) {
        this.initializeId = initializeId;
        this.absentHeaderVersion = absentHeaderVersion;
    }

    public static McpSessionHandshake named(String initializeId) {
        return new McpSessionHandshake(initializeId, HandshakeVersion.latest());
    }

    public static McpSessionHandshake defaultingTo(String initializeId, String absentHeaderVersion) {
        return new McpSessionHandshake(initializeId, absentHeaderVersion);
    }

    public HttpRequest initialize(HttpRequest template) {
        String body = McpRequests.envelope(McpMethods.INITIALIZE,
                McpRequests.initializeParams(version(template), CLIENT_NAME, ExtensionVersion.CURRENT),
                McpRequestId.of(initializeId));
        return template.withRemovedHeader(McpHeaders.SESSION_ID).withBody(body);
    }

    public HttpRequest initialized(HttpRequest template, String sessionId) {
        return SessionHeaders.inSession(template, sessionId)
                .withBody(McpRequests.initialized(version(template), sessionId).body());
    }

    private String version(HttpRequest template) {
        return HandshakeVersion.of(template, absentHeaderVersion);
    }
}
