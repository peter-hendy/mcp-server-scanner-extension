package com.mcpscanner.core.oauth;

import java.util.Optional;
import java.util.Set;

public record OwnedCodeExchangeResult(int status, boolean validTokenResponse,
        boolean protectedOperationVerified, Optional<String> oauthErrorCode) {
    private static final Set<String> OAUTH_ERRORS = Set.of("invalid_request", "invalid_client",
        "invalid_grant", "unauthorized_client", "unsupported_grant_type", "invalid_scope");

    public OwnedCodeExchangeResult {
        oauthErrorCode = status == 400 && !validTokenResponse
            ? java.util.Objects.requireNonNull(oauthErrorCode).filter(OAUTH_ERRORS::contains)
            : Optional.empty();
    }

    public OwnedCodeExchangeResult(int status, boolean validTokenResponse,
            boolean protectedOperationVerified) {
        this(status, validTokenResponse, protectedOperationVerified, Optional.empty());
    }

    static OwnedCodeExchangeResult denied(TokenResponse response) {
        Object error = response.fields().get("error");
        return new OwnedCodeExchangeResult(response.status(), false, false,
            error instanceof String code ? Optional.of(code) : Optional.empty());
    }
}
