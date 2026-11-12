package com.mcpscanner.core.oauth;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OwnedRefreshExchangeTest {
    public static void main(String[] args) {
        independentGrantsCompareRegisteredAndOmittedAuthentication();
        invalidResponseNeverRunsProtectedVerifier();
        protectedOperationFailureDoesNotExportRefreshToken();
        System.out.println("OwnedRefreshExchangeTest: 3 cases passed");
    }

    private static void independentGrantsCompareRegisteredAndOmittedAuthentication() {
        List<TokenRequest> requests = new ArrayList<>();
        TokenExchange token = request -> {
            requests.add(request);
            return new TokenResponse(200, Map.of("access_token", "access-secret-" + requests.size(),
                "token_type", "Bearer", "scope", "tools:read tools:write"));
        };
        OAuthFlowEngine engine = new OAuthFlowEngine(token,
            request -> { throw new AssertionError("refresh must not authorize a code"); },
            new CredentialStore() {
                public java.util.Optional<OAuthCredential> load(CredentialBinding binding) {
                    throw new AssertionError("refresh probe must not load credential store");
                }
                public void save(CredentialBinding binding, OAuthCredential credential) {
                    throw new AssertionError("refresh probe must not save credential store");
                }
            }, Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
        var ordinary = engine.exchangeOwnedRefresh(configuration(), "grant-one",
            RefreshClientAuthentication.REGISTERED, Set.of("tools:read"), access -> false);
        var omitted = engine.exchangeOwnedRefresh(configuration(), "grant-two",
            RefreshClientAuthentication.OMIT, Set.of("tools:read", "tools:write"),
            access -> access.equals("access-secret-2"));
        require(ordinary.validTokenResponse() && !ordinary.protectedOperationVerified(),
            "registered control has a valid token response but no protected effect");
        require(omitted.validTokenResponse() && omitted.protectedOperationVerified(),
            "omitted-auth arm can carry a protected effect separately");
        require(omitted.grantedScopes().contains("tools:write"), "result exposes validated scopes only");
        require(!omitted.toString().contains("access-secret") && !omitted.toString().contains("grant-two"),
            "result redacts token and grant values");
        require(requests.get(0).headers().containsKey("Authorization")
            && !requests.get(1).headers().containsKey("Authorization"),
            "client authentication can be omitted independently of the grant");
        require(!requests.get(0).form().containsKey("client_id")
            && requests.get(1).form().get("client_id") != null,
            "the omitted-auth arm still identifies its client while the Basic arm uses only the header");
        require(requests.get(0).form().get("refresh_token").equals("grant-one")
            && requests.get(1).form().get("refresh_token").equals("grant-two"),
            "each arm uses its supplied independent refresh token");
        require(requests.get(1).form().get("scope").equals("tools:read tools:write"),
            "scope request is explicit and sorted");
    }

    private static void invalidResponseNeverRunsProtectedVerifier() {
        OAuthFlowEngine engine = new OAuthFlowEngine(
            request -> new TokenResponse(400, Map.of("error", "invalid_client", "private", "secret")),
            request -> { throw new AssertionError("browser must not run"); },
            new CredentialStore() {
                public java.util.Optional<OAuthCredential> load(CredentialBinding binding) { return java.util.Optional.empty(); }
                public void save(CredentialBinding binding, OAuthCredential credential) {
                    throw new AssertionError("store must not run");
                }
            }, Clock.systemUTC());
        var result = engine.exchangeOwnedRefresh(configuration(), "owned-refresh",
            RefreshClientAuthentication.OMIT, Set.of("tools:read"),
            access -> { throw new AssertionError("invalid token must not reach protected verifier"); });
        require(result.status() == 400 && !result.validTokenResponse() && !result.protectedOperationVerified(),
            "explicit rejection is retained without promoting a finding");
    }

    private static void protectedOperationFailureDoesNotExportRefreshToken() {
        OAuthFlowEngine engine = new OAuthFlowEngine(
            request -> new TokenResponse(200, Map.of("access_token", "private-access", "token_type", "Bearer")),
            request -> { throw new AssertionError("browser must not run"); },
            new CredentialStore() {
                public java.util.Optional<OAuthCredential> load(CredentialBinding binding) { return java.util.Optional.empty(); }
                public void save(CredentialBinding binding, OAuthCredential credential) {
                    throw new AssertionError("store must not run");
                }
            }, Clock.systemUTC());
        try {
            engine.exchangeOwnedRefresh(configuration(), "private-refresh",
                RefreshClientAuthentication.OMIT, Set.of("tools:read"),
                token -> { throw new IllegalStateException("verification failed for " + token); });
            throw new AssertionError("protected verifier failure must stop the arm");
        } catch (OAuthFlowException expected) {
            require(expected.reason() == OAuthFlowException.Reason.EXCHANGE_FAILED
                && !expected.getMessage().contains("private-access")
                && !expected.getMessage().contains("private-refresh"),
                "protected verifier failure does not export token or grant values");
        }
    }

    private static AuthorizationCodeConfig configuration() {
        URI issuer = URI.create("https://issuer.test");
        return new AuthorizationCodeConfig(issuer, URI.create("https://resource.test/mcp"),
            URI.create("https://issuer.test/authorize"), URI.create("https://issuer.test/token"),
            new RegisteredClient(issuer, "owned-client", "private-client-secret"),
            URI.create("http://127.0.0.1:49151/callback"), Set.of("tools:read"), true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
