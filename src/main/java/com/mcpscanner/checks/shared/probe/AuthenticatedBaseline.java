package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.mcp.HeaderMutation;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class AuthenticatedBaseline {

    private final Supplier<AuthStrategy> authStrategy;

    public AuthenticatedBaseline(Supplier<AuthStrategy> authStrategy) {
        this.authStrategy = authStrategy;
    }

    public HttpRequest requestFor(HttpRequest seed) {
        Map<String, String> headers = new LinkedHashMap<>(authStrategy.get().headers());
        headers.putAll(ScannerSentinels.stripAuthOnly());
        return HeaderMutation.apply(seed, List.of(), headers);
    }
}
