package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthHeaderNames;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.mcp.HeaderMutation;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class CredentialStripper implements UnaryOperator<HttpRequest> {

    private final Supplier<AuthStrategy> authStrategy;

    public CredentialStripper(Supplier<AuthStrategy> authStrategy) {
        this.authStrategy = authStrategy;
    }

    @Override
    public HttpRequest apply(HttpRequest request) {
        return HeaderMutation.apply(request, identityHeaderNames(), ScannerSentinels.stripAuthOnly());
    }

    public HttpRequest applyKeepingLiveSession(HttpRequest request) {
        return apply(request).withHeader(ScannerSentinels.KEEP_SESSION_HEADER, ScannerSentinels.SENTINEL_VALUE);
    }

    Set<String> identityHeaderNames() {
        Set<String> names = AuthHeaderNames.authBearingHeaderNames(authStrategy.get());
        names.add(McpHeaders.SESSION_ID);
        return names;
    }
}
