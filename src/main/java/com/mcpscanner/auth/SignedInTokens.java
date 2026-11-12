package com.mcpscanner.auth;

import java.util.Objects;
import java.util.Optional;

public record SignedInTokens(Optional<String> subject, AccessToken accessToken, Optional<String> refreshToken) {

    public SignedInTokens {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
    }
}
