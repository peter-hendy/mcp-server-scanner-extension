package com.mcpscanner.proxy;

import java.util.Map;

record MessagePostResult(int statusCode, String body, Map<String, String> headers, RecordedExchange exchange) {

    boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    boolean isRefusal() {
        return UpstreamRefusal.isRefusal(statusCode);
    }

    ProxyResponse toProxyResponse() {
        return new ProxyResponse(statusCode, "application/json", body, headers);
    }
}
