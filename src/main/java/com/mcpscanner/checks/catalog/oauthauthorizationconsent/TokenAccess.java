package com.mcpscanner.checks.catalog.oauthauthorizationconsent;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.shared.auth.CredentialSubstitution;
import com.mcpscanner.checks.shared.auth.ToolsListProbe;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.checks.shared.session.McpSessionHandshake;
import com.mcpscanner.checks.shared.session.SessionHeaders;
import com.mcpscanner.mcp.McpHeaders;

import java.util.Optional;
import java.util.function.Supplier;

final class TokenAccess {

    private static final String INITIALIZE_ID = "oauth-authorization-consent-initialize";

    private final Http http;
    private final HttpRequest seed;
    private final CredentialSubstitution credentials;
    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();
    private final McpSessionHandshake handshake = McpSessionHandshake.named(INITIALIZE_ID);
    private final ToolsListProbe toolsList = new ToolsListProbe(9_300);

    TokenAccess(Http http, HttpRequest seed, Supplier<AuthStrategy> authStrategy) {
        this.http = http;
        this.seed = seed.withRemovedHeader(McpHeaders.SESSION_ID);
        this.credentials = new CredentialSubstitution(authStrategy);
    }

    AccessJudgement judge(String accessToken) {
        HttpRequestResponse anonymous = inFreshSession(credentials.anonymous(seed));
        HttpRequestResponse withToken = inFreshSession(credentials.bearer(seed, accessToken));
        return new AccessJudgement(access(anonymous, withToken), anonymous, withToken);
    }

    private HttpRequestResponse inFreshSession(HttpRequest credentialed) {
        HttpRequestResponse initialize = http.sendRequest(handshake.initialize(credentialed));
        Optional<String> sessionId = SessionHeaders.issuedBy(initialize);
        if (sessionId.isEmpty()) {
            return oracle.refused(initialize) ? initialize : http.sendRequest(toolsList.on(credentialed));
        }
        http.sendRequest(handshake.initialized(credentialed, sessionId.get()));
        return http.sendRequest(toolsList.on(credentialed.withHeader(McpHeaders.SESSION_ID, sessionId.get())));
    }

    private Access access(HttpRequestResponse anonymous, HttpRequestResponse withToken) {
        if (oracle.successful(anonymous)) {
            return Access.ALREADY_OPEN;
        }
        return oracle.refusedByAuthentication(anonymous) && oracle.successful(withToken)
                ? Access.GRANTS_ACCESS : Access.NOT_SHOWN;
    }
}
