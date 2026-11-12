package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthHeaderNames;
import com.mcpscanner.auth.AuthStrategy;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class CredentialHeaders {

    private final Supplier<AuthStrategy> authStrategy;

    public CredentialHeaders(Supplier<AuthStrategy> authStrategy) {
        this.authStrategy = authStrategy;
    }

    public boolean carriedBy(HttpRequest request) {
        Set<String> names = AuthHeaderNames.authBearingHeaderNames(authStrategy.get());
        return request.headers().stream().anyMatch(header -> names.contains(header.name()));
    }

    public HttpRequest withMissingAdded(HttpRequest request) {
        HttpRequest supplemented = request;
        for (Map.Entry<String, String> header : authStrategy.get().headers().entrySet()) {
            if (!supplemented.hasHeader(header.getKey())) {
                supplemented = supplemented.withAddedHeader(header.getKey(), header.getValue());
            }
        }
        return supplemented;
    }
}
