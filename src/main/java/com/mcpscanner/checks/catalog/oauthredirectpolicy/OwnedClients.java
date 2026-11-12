package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.shared.oauth.ProbeClientRegistrar;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class OwnedClients {

    private final Supplier<Optional<OwnedClient>> loopback;
    private final Supplier<Optional<OwnedClient>> https;

    OwnedClients(ProbeClientRegistrar registrar) {
        this.loopback = memoised(() -> register(registrar, CallbackUris.loopbackRegistered()));
        this.https = memoised(() -> register(registrar, CallbackUris.httpsControl()));
    }

    Optional<OwnedClient> loopback() {
        return loopback.get();
    }

    Optional<OwnedClient> https() {
        return https.get();
    }

    private static Optional<OwnedClient> register(ProbeClientRegistrar registrar, URI callback) {
        List<String> redirectUris = List.of(callback.toString());
        return registrar.register(ProbeClientMetadata.redirectingTo(redirectUris)).clientListingAll(redirectUris)
                .map(client -> new OwnedClient(client, callback));
    }

    private static <T> Supplier<T> memoised(Supplier<T> delegate) {
        return new Supplier<>() {
            private T value;
            private boolean resolved;

            @Override
            public synchronized T get() {
                if (!resolved) {
                    value = delegate.get();
                    resolved = true;
                }
                return value;
            }
        };
    }
}
