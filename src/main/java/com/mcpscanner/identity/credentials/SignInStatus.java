package com.mcpscanner.identity.credentials;

import java.util.Objects;
import java.util.Optional;

public record SignInStatus(State state, Optional<String> reason) {

    public enum State { NEEDS_CREDENTIALS, QUEUED, SIGNING_IN, READY, FAILED }

    public SignInStatus {
        Objects.requireNonNull(state, "state");
        reason = reason == null ? Optional.empty() : reason;
    }

    public static SignInStatus of(State state) {
        return new SignInStatus(state, Optional.empty());
    }

    public static SignInStatus ready() {
        return of(State.READY);
    }

    public static SignInStatus failed(String reason) {
        return new SignInStatus(State.FAILED, Optional.of(reason));
    }
}
