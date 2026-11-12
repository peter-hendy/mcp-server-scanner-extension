package com.mcpscanner.http;

import burp.api.montoya.http.message.HttpRequestResponse;

final class BurpTempFileCopies implements TempFileCopies {

    @Override
    public HttpRequestResponse kept(HttpRequestResponse exchange) {
        if (exchange == null) {
            return null;
        }
        HttpRequestResponse copy = copied(exchange);
        return copy == null ? exchange : copy;
    }

    private static HttpRequestResponse copied(HttpRequestResponse exchange) {
        try {
            return exchange.copyToTempFile();
        } catch (RuntimeException failure) {
            return null;
        }
    }
}
