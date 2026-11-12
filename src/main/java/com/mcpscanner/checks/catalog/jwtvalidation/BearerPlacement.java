package com.mcpscanner.checks.catalog.jwtvalidation;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.HeaderMutation;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.List;

final class BearerPlacement {

    static final String AUTHORIZATION = "Authorization";
    static final String BEARER_PREFIX = "Bearer ";

    HttpRequest withBearer(HttpRequest seed, String token) {
        return HeaderMutation.apply(seed, List.of(AUTHORIZATION),
                ScannerSentinels.withStripAuth(AUTHORIZATION, BEARER_PREFIX + token));
    }

    HttpRequest withoutCredential(HttpRequest seed) {
        return HeaderMutation.apply(seed, List.of(AUTHORIZATION), ScannerSentinels.stripAuthOnly());
    }
}
