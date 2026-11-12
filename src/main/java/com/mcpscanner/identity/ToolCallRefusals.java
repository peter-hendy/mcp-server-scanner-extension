package com.mcpscanner.identity;

import java.util.Objects;
import java.util.Optional;

public final class ToolCallRefusals {

    private final IdentityRegistry registry;
    private final Listeners listeners = new Listeners();
    private Optional<String> refusedUserId = Optional.empty();
    private int refusedCount;

    public ToolCallRefusals(IdentityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void toolCallRefused() {
        if (recordRefusal()) {
            listeners.fire();
        }
    }

    public void replied(int status, String body) {
        if (AuthorisationRefusal.refuses(status, body)) {
            toolCallRefused();
        }
    }

    public boolean refusedWithoutCredentials() {
        Optional<String> refused;
        synchronized (this) {
            refused = refusedUserId;
        }
        return refused.filter(registry::isPrimary).isPresent() && !primaryHasCredential();
    }

    public int refusedCount() {
        int count;
        synchronized (this) {
            count = refusedCount;
        }
        return refusedWithoutCredentials() ? count : 0;
    }

    public synchronized void clear() {
        refusedUserId = Optional.empty();
        refusedCount = 0;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private synchronized boolean recordRefusal() {
        String primaryId = registry.primaryUser().id();
        if (registry.credential(primaryId).isPresent()) {
            return false;
        }
        if (refusedUserId.filter(primaryId::equals).isEmpty()) {
            refusedUserId = Optional.of(primaryId);
            refusedCount = 0;
        }
        refusedCount++;
        return true;
    }

    private boolean primaryHasCredential() {
        return registry.credential(registry.primaryUser().id()).isPresent();
    }
}
