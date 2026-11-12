package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.ConnectedIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IdentityConnection {

    boolean isConnected();

    boolean isConnecting();

    void reconnectAs(Runnable switchUser);

    Optional<ConnectedIdentity> connectedIdentity();

    static IdentityConnection of(BooleanSupplier connected, BooleanSupplier connecting,
                                 Consumer<Runnable> reconnectAs,
                                 Supplier<Optional<ConnectedIdentity>> connectedIdentity) {
        Objects.requireNonNull(connected, "connected");
        Objects.requireNonNull(connecting, "connecting");
        Objects.requireNonNull(reconnectAs, "reconnectAs");
        Objects.requireNonNull(connectedIdentity, "connectedIdentity");
        return new IdentityConnection() {
            @Override
            public boolean isConnected() {
                return connected.getAsBoolean();
            }

            @Override
            public boolean isConnecting() {
                return connecting.getAsBoolean();
            }

            @Override
            public void reconnectAs(Runnable switchUser) {
                reconnectAs.accept(switchUser);
            }

            @Override
            public Optional<ConnectedIdentity> connectedIdentity() {
                return connectedIdentity.get();
            }
        };
    }
}
