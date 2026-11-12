package com.mcpscanner.auth.oauth;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.connection.OperationRecovery;
import com.mcpscanner.core.connection.RecoveryResult;
import com.mcpscanner.core.oauth.BearerChallenge;
import com.mcpscanner.core.oauth.OAuthChallengeParser;
import com.mcpscanner.core.protocol.McpFlow;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InsufficientScopeReporter {
    private static final int FORBIDDEN = 403;
    private static final String INSUFFICIENT_SCOPE = "insufficient_scope";
    private static final String CHALLENGE_HEADER = "WWW-Authenticate";

    private final McpEventLog eventLog;
    private final Supplier<Set<String>> grantedScopes;
    private final OAuthChallengeParser parser = new OAuthChallengeParser();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public InsufficientScopeReporter(McpEventLog eventLog, Supplier<Set<String>> grantedScopes) {
        this.eventLog = eventLog;
        this.grantedScopes = grantedScopes;
    }

    public OperationRecovery watch(String operation, OperationRecovery delegate) {
        return response -> {
            RecoveryResult result = delegate.recover(response);
            if (result == RecoveryResult.STOP) {
                insufficientScopes(response).ifPresent(scopes -> reportOnce(operation, scopes));
            }
            return result;
        };
    }

    private Optional<Set<String>> insufficientScopes(McpFlow.HttpResponse response) {
        if (response.status() != FORBIDDEN) {
            return Optional.empty();
        }
        return parser.parse(challengeHeaders(response))
                .filter(challenge -> INSUFFICIENT_SCOPE.equals(challenge.error()))
                .map(BearerChallenge::scopes);
    }

    private static List<String> challengeHeaders(McpFlow.HttpResponse response) {
        return response.headers().entrySet().stream()
                .filter(header -> CHALLENGE_HEADER.equalsIgnoreCase(header.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private void reportOnce(String operation, Set<String> challenged) {
        String required = ScopeGrant.describe(challenged);
        if (reported.add(operation + "\n" + required)) {
            eventLog.warn(operation + " was refused with insufficient_scope: the server requires " + required
                    + " but the token was granted " + ScopeGrant.describe(grantedScopes.get())
                    + ". The authorization server withheld these scopes; reconnect as a user allowed "
                    + required + " to test it.");
        }
    }
}
