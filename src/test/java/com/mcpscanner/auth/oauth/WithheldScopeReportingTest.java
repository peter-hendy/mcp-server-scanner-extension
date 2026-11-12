package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.connection.RecoveryResult;
import com.mcpscanner.core.oauth.AuthorizationApproval;
import com.mcpscanner.core.oauth.AuthorizationCallback;
import com.mcpscanner.core.oauth.AuthorizationCodeConfig;
import com.mcpscanner.core.oauth.BrowserCallback;
import com.mcpscanner.core.oauth.RegisteredClient;
import com.mcpscanner.core.oauth.TokenResponse;
import com.mcpscanner.core.protocol.McpFlow;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WithheldScopeReportingTest {
    private static final URI ISSUER = URI.create("https://issuer.example");
    private static final URI CALLBACK = URI.create("http://127.0.0.1:8123/callback");
    private static final String ACCESS_TOKEN = "secret-access-token";

    private final McpEventLog eventLog = new McpEventLog(null);

    @Test
    void sessionExposesTheGrantedScopesSeparatelyFromTheRequestedOnes() {
        SharedOAuthSession session = authenticated("read");

        assertThat(session.requestedScopes()).containsExactlyInAnyOrder("read", "write");
        assertThat(session.grantedScopes()).containsExactly("read");
        assertThat(session.scopeGrant().withheld()).containsExactly("write");
    }

    @Test
    void sessionTreatsAnOmittedScopeAsTheRequestedSetBeingGranted() {
        SharedOAuthSession session = authenticated(null);

        assertThat(session.scopeGrant().withheldWarning()).isEmpty();
    }

    @Test
    void stoppedInsufficientScopeChallengeIsLoggedWithOperationChallengedAndGrantedScopes() {
        var provider = strategy(authenticated("read")).sharedCredentialProvider().orElseThrow();

        RecoveryResult result = provider.beginOperation("tools/call").recover(challenge("write"));

        assertThat(result).isEqualTo(RecoveryResult.STOP);
        assertThat(warnings()).containsExactly("tools/call was refused with insufficient_scope: the server "
                + "requires write but the token was granted read. The authorization server withheld these "
                + "scopes; reconnect as a user allowed write to test it.");
    }

    @Test
    void repeatedChallengeForTheSameOperationAndScopeIsLoggedOnce() {
        var provider = strategy(authenticated("read")).sharedCredentialProvider().orElseThrow();

        provider.beginOperation("tools/call").recover(challenge("write"));
        provider.beginOperation("tools/call").recover(challenge("write"));
        provider.beginOperation("resources/read").recover(challenge("write"));
        provider.beginOperation("tools/call").recover(challenge("read write"));

        assertThat(warnings()).hasSize(3);
    }

    @Test
    void otherRefusalsAreNotReportedAsWithheldScopes() {
        var provider = strategy(authenticated("read")).sharedCredentialProvider().orElseThrow();

        provider.beginOperation("tools/call").recover(new McpFlow.HttpResponse(403, Map.of(), "", true));
        provider.beginOperation("tools/call").recover(new McpFlow.HttpResponse(403,
                Map.of("WWW-Authenticate", "Bearer error=\"invalid_token\""), "", true));

        assertThat(warnings()).isEmpty();
    }

    @Test
    void challengeThatStepsUpIsNotReportedAsWithheld() {
        var provider = strategy(authenticated("read")).sharedCredentialProvider().orElseThrow();

        RecoveryResult result = provider.beginOperation("tools/call").recover(challenge("admin"));

        assertThat(result).isEqualTo(RecoveryResult.RETRY);
        assertThat(warnings()).isEmpty();
    }

    @Test
    void loggedLinesNeverContainTheAccessToken() {
        var provider = strategy(authenticated("read")).sharedCredentialProvider().orElseThrow();

        provider.beginOperation("tools/call").recover(challenge("write"));

        assertThat(eventLog.snapshot()).noneMatch(entry -> entry.message().contains(ACCESS_TOKEN));
    }

    private List<String> warnings() {
        return eventLog.snapshot().stream()
                .filter(entry -> entry.level() == McpEventLog.Level.WARN)
                .map(McpEventLog.LogEntry::message)
                .toList();
    }

    private OAuthAuthCodeStrategy strategy(SharedOAuthSession session) {
        return new OAuthAuthCodeStrategy(ISSUER, "client", session, Clock.systemUTC(), eventLog,
                ignored -> AuthorizationApproval.APPROVED);
    }

    private SharedOAuthSession authenticated(String grantedScope) {
        var config = new AuthorizationCodeConfig(ISSUER, URI.create("https://mcp.example/mcp"),
                ISSUER.resolve("/authorize"), ISSUER.resolve("/token"), new RegisteredClient(ISSUER, "client", null),
                CALLBACK, Set.of("read", "write"), true);
        SharedOAuthSession session = new SharedOAuthSession(config, request -> tokenResponse(grantedScope),
                request -> new BrowserCallback(CALLBACK, new AuthorizationCallback(request.parameters().get("state"),
                        ISSUER.toString(), "code", null, null)), Clock.systemUTC());
        session.authenticate();
        return session;
    }

    private static TokenResponse tokenResponse(String grantedScope) {
        Map<String, Object> body = new HashMap<>(Map.of("access_token", ACCESS_TOKEN, "token_type", "Bearer",
                "expires_in", 3600));
        if (grantedScope != null) {
            body.put("scope", grantedScope);
        }
        return new TokenResponse(200, body);
    }

    private static McpFlow.HttpResponse challenge(String scope) {
        return new McpFlow.HttpResponse(403, Map.of("WWW-Authenticate",
                "Bearer error=\"insufficient_scope\", scope=\"" + scope + "\""), "", true);
    }
}
