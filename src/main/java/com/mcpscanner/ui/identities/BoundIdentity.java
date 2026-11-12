package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityRegistry;

import java.util.Optional;
import java.util.function.Consumer;

final class BoundIdentity {

    private final IdentityRegistry registry;
    private String identityId;
    private boolean loading;

    BoundIdentity(IdentityRegistry registry) {
        this.registry = registry;
    }

    boolean rebind(Identity identity) {
        boolean changed = !identity.id().equals(identityId);
        identityId = identity.id();
        return changed;
    }

    void unbind() {
        identityId = null;
    }

    Optional<Identity> current() {
        return identityId == null ? Optional.empty() : registry.find(identityId);
    }

    void load(Runnable seeding) {
        loading = true;
        try {
            seeding.run();
        } finally {
            loading = false;
        }
    }

    void ifEditing(Consumer<Identity> edit) {
        if (!loading) {
            current().ifPresent(edit);
        }
    }
}
