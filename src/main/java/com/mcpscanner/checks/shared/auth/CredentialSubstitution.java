package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthHeaderNames;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.mcp.HeaderMutation;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.Map;
import java.util.function.Supplier;

public final class CredentialSubstitution {

    private static final String AUTHORIZATION = "Authorization";

    private final Supplier<AuthStrategy> authStrategy;

    public CredentialSubstitution(Supplier<AuthStrategy> authStrategy) {
        this.authStrategy = authStrategy;
    }

    public HttpRequest anonymous(HttpRequest request) {
        return withIdentity(request, ScannerSentinels.stripAuthOnly());
    }

    public HttpRequest bearer(HttpRequest request, String token) {
        return withAuthorization(request, "Bearer " + token);
    }

    public HttpRequest withAuthorization(HttpRequest request, String authorization) {
        return withIdentity(request, ScannerSentinels.withStripAuth(AUTHORIZATION, authorization));
    }

    public HttpRequest withIdentity(HttpRequest request, Map<String, String> identityHeaders) {
        return HeaderMutation.apply(request, AuthHeaderNames.authBearingHeaderNames(authStrategy.get()),
                identityHeaders);
    }
}
