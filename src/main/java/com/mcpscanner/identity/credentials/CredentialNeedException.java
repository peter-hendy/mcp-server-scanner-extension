package com.mcpscanner.identity.credentials;

import java.util.Objects;

public final class CredentialNeedException extends IllegalArgumentException {

    private final transient CredentialNeed need;

    public CredentialNeedException(String message, CredentialNeed need) {
        super(message);
        this.need = Objects.requireNonNull(need, "need");
    }

    public CredentialNeed need() {
        return need;
    }
}
