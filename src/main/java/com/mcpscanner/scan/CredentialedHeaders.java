package com.mcpscanner.scan;

import com.mcpscanner.auth.AuthStrategy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class CredentialedHeaders {

    private CredentialedHeaders() {
    }

    public static Supplier<Map<String, String>> of(Supplier<Map<String, String>> sessionHeaders,
                                                   AuthStrategy strategy) {
        return () -> {
            Map<String, String> headers = new LinkedHashMap<>(sessionHeaders.get());
            headers.putAll(strategy.headers());
            return headers;
        };
    }
}
