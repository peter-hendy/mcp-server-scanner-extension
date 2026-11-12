package com.mcpscanner.mcp;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;

public final class TooManyRequests {

    public static final int STATUS = 429;

    private TooManyRequests() {
    }

    public static boolean answered(HttpRequestResponse exchange) {
        return exchange != null && is(exchange.response());
    }

    public static boolean is(HttpResponse response) {
        return response != null && response.statusCode() == STATUS;
    }
}
