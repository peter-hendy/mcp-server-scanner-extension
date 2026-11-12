package com.mcpscanner.http;

import burp.api.montoya.http.message.HttpRequestResponse;

public interface TempFileCopies {

    TempFileCopies IN_MEMORY = exchange -> exchange;

    HttpRequestResponse kept(HttpRequestResponse exchange);

    static TempFileCopies burp() {
        return new BurpTempFileCopies();
    }
}
