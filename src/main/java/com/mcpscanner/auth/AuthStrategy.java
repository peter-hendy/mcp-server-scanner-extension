package com.mcpscanner.auth;

import com.mcpscanner.auth.oauth.SignInEvidence;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public interface AuthStrategy {

    Map<String, String> headers();

    default void validateEndpoint(URI endpoint) {
    }

    default Set<String> contributedHeaderNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(headers().keySet());
        return names;
    }

    default java.util.Optional<com.mcpscanner.core.connection.CredentialProvider> sharedCredentialProvider() {
        return java.util.Optional.empty();
    }

    default boolean supportsRefresh() {
        return false;
    }

    default boolean refresh() {
        return false;
    }

    default boolean renew() {
        return refresh();
    }

    default Map<String, String> currentHeaders() {
        if (!supportsRefresh()) {
            return headers();
        }
        return currentToken().map(token -> Map.of(AuthHeaders.AUTHORIZATION_HEADER,
                AuthHeaders.BEARER_PREFIX + token.value())).orElse(Map.of());
    }

    default java.util.Optional<AccessToken> currentToken() {
        return java.util.Optional.empty();
    }

    default java.util.Optional<SignedInTokens> signedInTokens() {
        return java.util.Optional.empty();
    }

    default java.util.Optional<SignInEvidence> signInEvidence() {
        return java.util.Optional.empty();
    }

    default boolean isTerminallyFailed() {
        return false;
    }

    default void setTerminalFailureListener(Runnable listener) {
    }

    default java.util.Optional<com.mcpscanner.auth.grant.RefreshGrantStore> refreshGrantStore() {
        return java.util.Optional.empty();
    }
}
