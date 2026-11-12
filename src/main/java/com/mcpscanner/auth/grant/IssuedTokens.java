package com.mcpscanner.auth.grant;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record IssuedTokens(String accessToken, Optional<String> refreshToken, Duration lifetime) {

    public IssuedTokens {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(lifetime, "lifetime");
    }

    @Override
    public String toString() {
        return "IssuedTokens[redacted, lifetime=" + lifetime + "]";
    }
}
