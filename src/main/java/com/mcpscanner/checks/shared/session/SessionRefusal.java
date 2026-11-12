package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.jsonrpc.McpReply;

import java.util.Set;

public final class SessionRefusal {

    private static final Set<Integer> CREDENTIALS_REQUIRED = Set.of(401, 403);
    private static final Set<Integer> REJECTED = Set.of(400, 401, 403, 404);

    private SessionRefusal() {
    }

    public static boolean refuses(HttpRequestResponse exchange) {
        return answered(exchange) && (REJECTED.contains(status(exchange)) || answeredWithError(exchange));
    }

    private static boolean answeredWithError(HttpRequestResponse exchange) {
        return status(exchange) / 100 == 2
                && !McpReply.answering(exchange, McpReply::isResponse).error().isMissingNode();
    }

    public static boolean requiresCredentials(HttpRequestResponse exchange) {
        return answered(exchange) && CREDENTIALS_REQUIRED.contains(status(exchange));
    }

    private static boolean answered(HttpRequestResponse exchange) {
        return exchange != null && exchange.response() != null;
    }

    private static int status(HttpRequestResponse exchange) {
        return exchange.response().statusCode();
    }
}
