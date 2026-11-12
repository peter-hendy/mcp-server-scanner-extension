package com.mcpscanner.http;

import burp.api.montoya.http.message.HttpRequestResponse;

@FunctionalInterface
public interface ExchangeCounter {

    HttpRequestResponse counted(HttpRequestResponse exchange);
}
