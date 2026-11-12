package com.mcpscanner.core.connection;

import com.mcpscanner.core.oauth.AuthorizationApproval;
import com.mcpscanner.core.oauth.CredentialBinding;
import com.mcpscanner.core.oauth.OAuthChallengePolicy;
import com.mcpscanner.core.oauth.OAuthRetryState;
import com.mcpscanner.core.protocol.McpFlow;
import java.util.Map;
import java.util.function.Supplier;

public final class OAuthCredentialProvider implements CredentialProvider {
    private final CredentialBinding binding;
    private final OAuthCredentialAccess access;
    private final java.util.function.Function<java.util.Set<String>, AuthorizationApproval> approval;
    private final java.util.Set<String> approvedScopes = new java.util.HashSet<>();
    private final OAuthChallengePolicy policy = new OAuthChallengePolicy();

    public OAuthCredentialProvider(CredentialBinding binding, OAuthCredentialAccess access, Supplier<AuthorizationApproval> approval) {
        this(binding, access, scopes -> approval.get());
    }

    public OAuthCredentialProvider(CredentialBinding binding, OAuthCredentialAccess access,
            java.util.function.Function<java.util.Set<String>, AuthorizationApproval> approval) {
        this.binding = java.util.Objects.requireNonNull(binding);
        this.access = java.util.Objects.requireNonNull(access);
        this.approval = java.util.Objects.requireNonNull(approval);
    }

    public Map<String, String> headers() {
        return access.current().map(credential -> {
            policy.decide(200, java.util.Optional.empty(), credential.binding(), OAuthRetryState.start(binding, "headers"),
                    true, false, AuthorizationApproval.REQUIRE_PROMPT);
            return CredentialProviders.bearer(credential.accessToken()).headers();
        }).orElseGet(Map::of);
    }

    public OperationRecovery beginOperation(String operation) { return new Recovery(operation); }

    private synchronized CredentialBinding operationBinding() {
        var scopes = new java.util.HashSet<>(binding.scopes());
        scopes.addAll(approvedScopes);
        access.current().ifPresent(credential -> scopes.addAll(credential.grantedScopes()));
        return new CredentialBinding(binding.issuer(), binding.resource(), binding.clientId(), scopes, binding.grantMode());
    }

    private synchronized void rememberApproval(java.util.Set<String> scopes) { approvedScopes.addAll(scopes); }
    @Override public String toString() { return "OAuthCredentialProvider[redacted]"; }

    private final class Recovery implements OperationRecovery {
        private OAuthRetryState state;
        private Recovery(String operation) { state = OAuthRetryState.start(operationBinding(), operation); }

        public RecoveryResult recover(McpFlow.HttpResponse response) {
            var current = access.current();
            var challenge = BearerChallenges.parse(response);
            var decision = policy.decide(response.status(), challenge,
                    current.map(value -> value.binding()).orElse(binding), state, current.isPresent(),
                    access.refreshAvailable(), AuthorizationApproval.REQUIRE_PROMPT);
            if (decision.action() == com.mcpscanner.core.oauth.OAuthChallengeDecision.Action.APPROVAL_REQUIRED)
                decision = policy.decide(response.status(), challenge, current.map(value -> value.binding()).orElse(binding),
                        state, current.isPresent(), access.refreshAvailable(), approval.apply(decision.scopes()));
            state = decision.nextState();
            return switch (decision.action()) {
                case REFRESH -> { access.refresh(); yield RecoveryResult.RETRY; }
                case AUTHORIZE -> { access.authorize(decision.scopes()); rememberApproval(decision.scopes()); yield RecoveryResult.RETRY; }
                case APPROVAL_REQUIRED -> RecoveryResult.APPROVAL_REQUIRED;
                case DENIED -> RecoveryResult.DENIED;
                default -> RecoveryResult.STOP;
            };
        }
    }
}
