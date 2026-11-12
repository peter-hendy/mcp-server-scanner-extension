package com.mcpscanner.identity;

import java.util.Objects;

public record ConnectedIdentity(CredentialFingerprint fingerprint, String name, IdentityAuthKind authKind,
                                boolean anonymous) {

    public ConnectedIdentity {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(authKind, "authKind");
    }

    public String identityId() {
        return fingerprint.identityId();
    }
}
