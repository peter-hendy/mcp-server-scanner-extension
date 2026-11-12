package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.auth.grant.IssuedTokens;

import java.util.Optional;
import java.util.Set;

record RefreshExchange(ClientAuthentication authentication, HttpRequestResponse exchange,
                       Optional<IssuedTokens> tokens, Optional<Set<String>> grantedScope) {

    boolean issued() {
        return tokens.isPresent();
    }

    boolean refused() {
        if (issued() || exchange == null || exchange.response() == null) {
            return false;
        }
        int status = exchange.response().statusCode();
        return status == 400 || status == 401 || status == 403;
    }

    int status() {
        return exchange == null || exchange.response() == null ? 0 : exchange.response().statusCode();
    }
}
