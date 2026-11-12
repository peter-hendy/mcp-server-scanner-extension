package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.oauth.AuthorizationApproval;
import com.mcpscanner.core.oauth.AuthorizationCallback;
import com.mcpscanner.core.oauth.AuthorizationCodeConfig;
import com.mcpscanner.core.oauth.BrowserCallback;
import com.mcpscanner.core.oauth.RegisteredClient;
import com.mcpscanner.core.oauth.RegistrationMode;
import com.mcpscanner.core.oauth.TokenExchange;
import com.mcpscanner.core.oauth.TokenRequest;
import com.mcpscanner.core.oauth.TokenResponse;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TestOAuthStrategies {

    private static final URI CALLBACK = URI.create("http://127.0.0.1:8123/callback");

    private TestOAuthStrategies() {
    }

    public record Client(String clientId, String secret, String tokenEndpointAuthMethod, RegistrationMode mode) {
    }

    public record Login(URI issuer, URI resource, List<String> scopes, Client client, String accessToken,
                        String refreshToken, Instant expiresAt, Clock clock) {
    }

    public static OAuthAuthCodeStrategy authenticated(Login login, TokenExchange refreshes) {
        var config = new AuthorizationCodeConfig(login.issuer(), login.resource(), login.issuer().resolve("/authorize"),
                login.issuer().resolve("/token"),
                new RegisteredClient(login.issuer(), login.client().clientId(), login.client().secret()),
                CALLBACK, Set.copyOf(login.scopes()), true, login.client().tokenEndpointAuthMethod());
        TokenExchange exchange = request -> isCodeExchange(request) ? issued(login) : refreshes.exchange(request);
        SharedOAuthSession session = new SharedOAuthSession(config, exchange,
                request -> new BrowserCallback(CALLBACK, new AuthorizationCallback(request.parameters().get("state"),
                        login.issuer().toString(), "code", null, null)), login.clock());
        session.authenticate();
        return new OAuthAuthCodeStrategy(login.issuer(), login.client().clientId(), session, login.clock(),
                McpEventLog.noop(), ignored -> AuthorizationApproval.REQUIRE_PROMPT, login.client().mode());
    }

    public static TokenExchange refusingRefresh() {
        return request -> new TokenResponse(400, Map.of("error", "invalid_grant"));
    }

    public static TokenResponse tokens(String accessToken, String refreshToken, Duration lifetime) {
        Map<String, Object> body = new HashMap<>(Map.of("access_token", accessToken, "token_type", "Bearer",
                "expires_in", lifetime.toSeconds()));
        if (refreshToken != null) {
            body.put("refresh_token", refreshToken);
        }
        return new TokenResponse(200, body);
    }

    private static boolean isCodeExchange(TokenRequest request) {
        return "authorization_code".equals(request.form().get("grant_type"));
    }

    private static TokenResponse issued(Login login) {
        return tokens(login.accessToken(), login.refreshToken(),
                Duration.between(login.clock().instant(), login.expiresAt()));
    }
}
