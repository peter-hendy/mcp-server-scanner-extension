package com.mcpscanner.http;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConnectionClose {

    public static final String HEADER = "Connection";
    public static final String VALUE = "close";

    private ConnectionClose() {
    }

    public static HttpRequest on(HttpRequest request) {
        return request.withHeader(HEADER, VALUE);
    }

    public static HttpRequest withoutOurs(HttpRequest request) {
        return VALUE.equalsIgnoreCase(request.headerValue(HEADER)) ? request.withRemovedHeader(HEADER) : request;
    }

    public static Map<String, List<String>> without(Map<String, List<String>> headers) {
        Map<String, List<String>> withoutConnection = new LinkedHashMap<>(headers);
        withoutConnection.keySet().removeIf(ConnectionClose::isConnectionHeader);
        return withoutConnection;
    }

    public static boolean isConnectionHeader(String name) {
        return HEADER.equalsIgnoreCase(name);
    }
}
