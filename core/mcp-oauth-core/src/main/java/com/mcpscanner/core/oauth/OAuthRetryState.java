package com.mcpscanner.core.oauth;

import java.util.Set;

public final class OAuthRetryState {
    private final CredentialBinding binding;
    private final String operation;
    private final int attempts;
    private final boolean refreshed;
    private final Set<String> scopes;
    private final Set<Set<String>> authorizedScopes;
    private final boolean terminal;

    private OAuthRetryState(CredentialBinding binding, String operation, int attempts, boolean refreshed,
                            Set<String> scopes, Set<Set<String>> authorizedScopes, boolean terminal) {
        this.binding = binding;
        this.operation = operation;
        this.attempts = attempts;
        this.refreshed = refreshed;
        this.scopes = Set.copyOf(scopes);
        this.authorizedScopes = Set.copyOf(authorizedScopes);
        this.terminal = terminal;
    }
    public static OAuthRetryState start(CredentialBinding binding, String operation) {
        if (binding == null || operation == null || operation.isBlank()) throw invalid();
        return new OAuthRetryState(binding, operation, 0, false, binding.scopes(), Set.of(), false);
    }
    public int attempts() { return attempts; }
    public String operation() { return operation; }
    public Set<String> scopes() { return scopes; }
    boolean refreshed() { return refreshed; }
    boolean terminal() { return terminal; }
    boolean previouslyAuthorized(Set<String> requested) { return authorizedScopes.contains(requested); }
    void validateBinding(CredentialBinding current) {
        if (current != null && binding.grantMode() != current.grantMode()) throw invalid();
        if (current == null || !binding.resource().toString().equals(current.resource().toString()))
            throw new OAuthPolicyException(OAuthPolicyException.Reason.RESOURCE_MISMATCH);
        if (!binding.issuer().toString().equals(current.issuer().toString()) || !binding.clientId().equals(current.clientId()))
            throw new OAuthPolicyException(OAuthPolicyException.Reason.ISSUER_MISMATCH);
    }
    OAuthRetryState refresh() {
        return new OAuthRetryState(binding, operation, attempts + 1, true, scopes, authorizedScopes, false);
    }
    OAuthRetryState authorize(Set<String> requested) {
        var attempted = new java.util.HashSet<>(authorizedScopes);
        attempted.add(Set.copyOf(requested));
        return new OAuthRetryState(binding, operation, attempts + 1, refreshed, requested, attempted, false);
    }
    OAuthRetryState stop() {
        return new OAuthRetryState(binding, operation, attempts, refreshed, scopes, authorizedScopes, true);
    }
    private static OAuthPolicyException invalid() { return new OAuthPolicyException(OAuthPolicyException.Reason.INVALID_RETRY_STATE); }
    @Override public String toString() { return "OAuthRetryState[attempts=" + attempts + "]"; }
}
