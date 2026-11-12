package com.mcpscanner.identity;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;

import java.util.Objects;
import java.util.Optional;

public final class ConnectionFreshness {

    private final IdentityRegistry registry;

    public ConnectionFreshness(IdentityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public ConnectedIdentity snapshot(String identityId, AuthStrategy connectionAuth) {
        Identity user = registry.find(identityId).orElseGet(registry::primaryUser);
        return new ConnectedIdentity(fingerprintOf(user), user.name(), user.authKind(),
                connectionAuth instanceof NoAuthStrategy);
    }

    public Optional<StaleConnection> check(ConnectedIdentity connected) {
        return registry.find(connected.identityId())
                .flatMap(user -> changed(user, connected));
    }

    private Optional<StaleConnection> changed(Identity user, ConnectedIdentity connected) {
        return fingerprintOf(user).equals(connected.fingerprint()) ? Optional.empty()
                : Optional.of(new StaleConnection(user));
    }

    private CredentialFingerprint fingerprintOf(Identity user) {
        return CredentialFingerprint.of(user, registry.credential(user.id()));
    }
}
