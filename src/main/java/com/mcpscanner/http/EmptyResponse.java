package com.mcpscanner.http;

import burp.api.montoya.http.message.responses.HttpResponse;

public final class EmptyResponse {

    private EmptyResponse() {
    }

    public static boolean is(HttpResponse response) {
        return response.statusCode() == 0 && response.toByteArray().length() == 0;
    }
}
