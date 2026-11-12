package com.mcpscanner.config;

@FunctionalInterface
public interface ClientIdentityProvider {

    ClientIdentity current();

    static ClientIdentityProvider defaults() {
        return ClientIdentity::defaults;
    }

    static ClientIdentityProvider stored(ExtensionConfigStore store) {
        return () -> {
            try {
                return store.clientIdentity();
            } catch (IllegalArgumentException invalidStoredValue) {
                return ClientIdentity.defaults();
            }
        };
    }
}
