package com.mcpscanner.checks.catalog.jwtvalidation;

import java.util.List;
import java.util.function.Supplier;

final class ForgeryContext {

    private final CompactJwt captured;
    private final Supplier<List<PublicKeyMaterial>> publicKeyDiscovery;
    private List<PublicKeyMaterial> publicKeys;

    ForgeryContext(CompactJwt captured, Supplier<List<PublicKeyMaterial>> publicKeyDiscovery) {
        this.captured = captured;
        this.publicKeyDiscovery = publicKeyDiscovery;
    }

    CompactJwt captured() {
        return captured;
    }

    List<PublicKeyMaterial> publicKeys() {
        if (publicKeys == null) {
            publicKeys = List.copyOf(publicKeyDiscovery.get());
        }
        return publicKeys;
    }
}
