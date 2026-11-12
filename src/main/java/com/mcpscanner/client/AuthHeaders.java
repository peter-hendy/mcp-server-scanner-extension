package com.mcpscanner.client;

import com.mcpscanner.auth.AuthStrategy;

import java.util.Collections;
import java.util.Map;

final class AuthHeaders {

    private AuthHeaders() {}

    static Map<String, String> of(AuthStrategy auth) {
        Map<String, String> headers = auth.headers();
        return headers != null ? headers : Collections.emptyMap();
    }
}
