package com.mcpscanner.http;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;

public interface OutsideBurpSends {

    void sentOutsideBurp(HttpRequestResponse exchange);

    static void report(Http http, HttpRequestResponse exchange) {
        if (http instanceof OutsideBurpSends observer) {
            observer.sentOutsideBurp(exchange);
        }
    }
}
