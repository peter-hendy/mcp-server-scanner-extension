package com.mcpscanner.core.oauth;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import static com.mcpscanner.core.oauth.OAuthChallengeDecision.Action;

public final class OAuthChallengePolicy {
    private final int maxRetries;
    public OAuthChallengePolicy() { this(2); }
    public OAuthChallengePolicy(int maxRetries) {
        if (maxRetries < 1 || maxRetries > 3) throw new OAuthPolicyException(OAuthPolicyException.Reason.INVALID_RETRY_STATE);
        this.maxRetries = maxRetries;
    }
    public OAuthChallengeDecision decide(int status, Optional<BearerChallenge> challenge, CredentialBinding binding,
                                         OAuthRetryState state, boolean tokenPresent, boolean refreshAvailable,
                                         AuthorizationApproval approval) {
        state.validateBinding(binding);
        boolean insufficient = status == 403 && challenge.isPresent() && "insufficient_scope".equals(challenge.get().error());
        if (status != 401 && !insufficient) return decision(Action.NONE, state.scopes(), state);
        if (state.terminal() || state.attempts() >= maxRetries) return decision(Action.PERMANENT_FAILURE, state.scopes(), state.stop());
        if (status == 401 && tokenPresent && refreshAvailable && !state.refreshed())
            return decision(Action.REFRESH, state.scopes(), state.refresh());
        var requested = new HashSet<>(state.scopes());
        challenge.ifPresent(value -> requested.addAll(value.scopes()));
        if (insufficient && (challenge.get().scopes().isEmpty() || state.scopes().containsAll(requested)) || state.previouslyAuthorized(requested))
            return decision(Action.PERMANENT_FAILURE, requested, state.stop());
        if (approval == AuthorizationApproval.DENIED) return decision(Action.DENIED, requested, state.stop());
        if (approval != AuthorizationApproval.APPROVED) return decision(Action.APPROVAL_REQUIRED, requested, state);
        return decision(Action.AUTHORIZE, requested, state.authorize(requested));
    }
    private OAuthChallengeDecision decision(Action action, Set<String> scopes, OAuthRetryState state) {
        return new OAuthChallengeDecision(action, scopes, state);
    }
}
