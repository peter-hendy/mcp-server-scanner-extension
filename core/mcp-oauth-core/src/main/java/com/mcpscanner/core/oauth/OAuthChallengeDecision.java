package com.mcpscanner.core.oauth;

import java.util.Set;

public record OAuthChallengeDecision(Action action, Set<String> scopes, OAuthRetryState nextState) {
    public enum Action { NONE, REFRESH, AUTHORIZE, APPROVAL_REQUIRED, DENIED, PERMANENT_FAILURE }
    public OAuthChallengeDecision { scopes = Set.copyOf(scopes); }
    @Override public String toString() { return "OAuthChallengeDecision[action=" + action + "]"; }
}
