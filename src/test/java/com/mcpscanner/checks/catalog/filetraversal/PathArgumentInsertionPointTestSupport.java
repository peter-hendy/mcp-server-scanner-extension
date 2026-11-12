package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.McpObjectMapper;

final class PathArgumentInsertionPointTestSupport {

    private PathArgumentInsertionPointTestSupport() {}

    static String pathOf(HttpRequest request) {
        try {
            return McpObjectMapper.INSTANCE.readTree(request.bodyToString())
                    .path("params").path("arguments").path("path").asText();
        } catch (Exception unparseable) {
            throw new IllegalStateException(unparseable);
        }
    }
}
