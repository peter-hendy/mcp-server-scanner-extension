package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.jsonrpc.McpReply;

final class PingReplies {

    private PingReplies() {
    }

    static String statusOf(HttpRequestResponse exchange) {
        return exchange == null || exchange.response() == null
                ? "no response" : "HTTP " + exchange.response().statusCode();
    }

    static boolean answered(HttpRequestResponse exchange) {
        return McpReply.answering(exchange, McpReply::isResponse).successfulResult().isPresent();
    }
}
