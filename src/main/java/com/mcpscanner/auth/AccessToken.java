package com.mcpscanner.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AccessToken(String value, Optional<Instant> expiresAt) {

    public AccessToken {
        Objects.requireNonNull(value);
        expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
    }

    public static AccessToken of(String value, Instant expiresAt) {
        return new AccessToken(value, Optional.ofNullable(expiresAt));
    }
}
