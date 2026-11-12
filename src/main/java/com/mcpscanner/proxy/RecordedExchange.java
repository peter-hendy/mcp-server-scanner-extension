package com.mcpscanner.proxy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

record RecordedExchange(URI uri, String method, Map<String, List<String>> requestHeaders, String requestBody,
                        int status, Map<String, List<String>> responseHeaders, byte[] responseBody) {

    RecordedExchange {
        requestHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(requestHeaders));
        responseHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(responseHeaders));
        responseBody = responseBody.clone();
    }

    @Override
    public byte[] responseBody() {
        return responseBody.clone();
    }

    long bodyBytes() {
        return requestBodyBytes().length + (long) responseBody.length;
    }

    byte[] requestBodyBytes() {
        return Objects.requireNonNullElse(requestBody, "").getBytes(StandardCharsets.UTF_8);
    }

    RecordedExchange withResponseBody(byte[] body) {
        return new RecordedExchange(uri, method, requestHeaders, requestBody, status, responseHeaders, body);
    }
}
