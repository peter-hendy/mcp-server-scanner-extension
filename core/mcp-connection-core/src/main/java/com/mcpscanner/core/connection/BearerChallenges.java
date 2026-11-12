package com.mcpscanner.core.connection;

import com.mcpscanner.core.oauth.BearerChallenge;
import com.mcpscanner.core.oauth.OAuthChallengeParser;
import com.mcpscanner.core.oauth.OAuthPolicyException;
import com.mcpscanner.core.protocol.McpFlow;
import java.util.Map;
import java.util.Optional;

final class BearerChallenges {
    private BearerChallenges() {}

    static Optional<BearerChallenge> parse(McpFlow.HttpResponse response) {
        var values = response.headers().entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase("WWW-Authenticate"))
                .map(Map.Entry::getValue).toList();
        return new OAuthChallengeParser().parse(values);
    }

    static boolean genuine(McpFlow.HttpResponse response) {
        if (response == null || response.status() != 401 && response.status() != 403) return false;
        try {
            return parse(response).isPresent();
        } catch (OAuthPolicyException malformed) {
            return false;
        }
    }
}
