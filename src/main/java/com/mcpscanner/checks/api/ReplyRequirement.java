package com.mcpscanner.checks.api;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.mcp.McpRequestDetector;

public final class ReplyRequirement {

    private ReplyRequirement() {}

    public static boolean permits(McpCheck check, HttpRequestResponse baseRequestResponse) {
        return !check.requiresReply() || !McpRequestDetector.isNotification(baseRequestResponse.request());
    }
}
