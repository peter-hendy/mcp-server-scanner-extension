package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.core.protocol.McpRequests;

public final class HandshakeVersion {

    private HandshakeVersion() {
    }

    public static String of(HttpRequest template) {
        return of(template, latest());
    }

    public static String of(HttpRequest template, String absentHeaderVersion) {
        String declared = template.headerValue(McpHeaders.PROTOCOL_VERSION);
        if (declared == null || declared.isBlank()) {
            return absentHeaderVersion;
        }
        return McpRequests.supportedHandshake(declared) ? declared : latest();
    }

    static String latest() {
        return McpRequests.supportedInitializeVersions().get(0);
    }
}
