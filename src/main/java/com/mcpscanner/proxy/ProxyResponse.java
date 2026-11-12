package com.mcpscanner.proxy;

import java.util.LinkedHashMap;
import java.util.Map;

record ProxyResponse(int statusCode, String contentType, String body, Map<String, String> headers) {
    ProxyResponse {
        headers = Map.copyOf(headers);
    }

    ProxyResponse(int statusCode, String contentType, String body) {
        this(statusCode, contentType, body, Map.of());
    }

    ProxyResponse withHeader(String name, String value) {
        return withHeaders(Map.of(name, value));
    }

    ProxyResponse withHeaders(Map<String, String> added) {
        Map<String, String> merged = new LinkedHashMap<>(headers);
        merged.putAll(added);
        return new ProxyResponse(statusCode, contentType, body, merged);
    }
}
