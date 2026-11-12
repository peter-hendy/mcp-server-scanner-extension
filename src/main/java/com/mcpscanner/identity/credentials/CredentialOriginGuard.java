package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.HttpOrigin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class CredentialOriginGuard {

    private final IdentityRegistry registry;
    private final McpEventLog eventLog;
    private final Supplier<String> currentEndpoint;
    private final Map<String, BoundCredential> bindings = new HashMap<>();

    private CredentialOriginGuard(IdentityRegistry registry, McpEventLog eventLog, Supplier<String> currentEndpoint) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
        this.currentEndpoint = Objects.requireNonNull(currentEndpoint, "currentEndpoint");
    }

    public static CredentialOriginGuard watching(IdentityRegistry registry, McpEventLog eventLog,
                                                 Supplier<String> currentEndpoint) {
        CredentialOriginGuard guard = new CredentialOriginGuard(registry, eventLog, currentEndpoint);
        guard.bindNewCredentials();
        registry.addListener(guard::bindNewCredentials);
        return guard;
    }

    public Optional<CredentialsCleared> commit(String endpoint) {
        Optional<HttpOrigin> committed = originOf(endpoint);
        if (committed.isEmpty()) {
            return Optional.empty();
        }
        List<Identity> foreign = foreignHolders(committed.get());
        if (foreign.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(clear(foreign, committed.get()));
    }

    private synchronized void bindNewCredentials() {
        Optional<HttpOrigin> origin = originOf(currentEndpoint.get());
        bindings.keySet().removeIf(id -> registry.credential(id).isEmpty());
        for (Identity identity : registry.identities()) {
            registry.credential(identity.id())
                    .filter(credential -> !isBound(identity.id(), credential))
                    .ifPresent(credential -> bindings.put(identity.id(), new BoundCredential(credential, origin)));
        }
    }

    private boolean isBound(String id, AuthStrategy credential) {
        BoundCredential binding = bindings.get(id);
        return binding != null && binding.credential() == credential;
    }

    private synchronized List<Identity> foreignHolders(HttpOrigin committed) {
        bindNewCredentials();
        bindings.replaceAll((id, binding) -> binding.boundTo(committed));
        return registry.identities().stream()
                .filter(identity -> bindings.containsKey(identity.id()))
                .filter(identity -> !bindings.get(identity.id()).origin().orElseThrow().equals(committed))
                .toList();
    }

    private CredentialsCleared clear(List<Identity> holders, HttpOrigin committed) {
        List<HttpOrigin> previous;
        synchronized (this) {
            previous = holders.stream().map(holder -> bindings.get(holder.id()).origin().orElseThrow())
                    .distinct().toList();
        }
        holders.forEach(holder -> registry.removeCredential(holder.id()));
        CredentialsCleared cleared = new CredentialsCleared(previous, committed,
                holders.stream().map(Identity::name).toList());
        eventLog.info(cleared.message());
        return cleared;
    }

    private static Optional<HttpOrigin> originOf(String endpoint) {
        return endpoint == null ? Optional.empty() : HttpOrigin.parse(endpoint.trim());
    }

    private record BoundCredential(AuthStrategy credential, Optional<HttpOrigin> origin) {

        BoundCredential boundTo(HttpOrigin fallback) {
            return origin.isPresent() ? this : new BoundCredential(credential, Optional.of(fallback));
        }
    }
}
