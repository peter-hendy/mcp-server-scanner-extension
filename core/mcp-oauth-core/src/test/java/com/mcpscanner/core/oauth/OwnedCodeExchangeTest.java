package com.mcpscanner.core.oauth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OwnedCodeExchangeTest {
    public static void main(String[] args) {
        challengeAndVerifierAreSelectedPerFreshCode();
        invalidStateStopsBeforeTokenExchange();
        invalidTokenResponseIsRedactedAndNotSaved();
        protectedOperationVerifierReceivesTokenOnlyAfterValidation();
        protectedOperationFailureDoesNotExportToken();
        structuredOAuthDenialIsDistinctFromGenericHttpFailure();
        incompleteTokenExchangeCannotClassifyDenial();
        System.out.println("OwnedCodeExchangeTest: 7 cases passed");
    }

    private static void challengeAndVerifierAreSelectedPerFreshCode() {
        List<AuthorizationRequest> authorizations = new ArrayList<>();
        List<TokenRequest> exchanges = new ArrayList<>();
        BrowserAuthorization browser = request -> {
            authorizations.add(request);
            return new BrowserCallback(configuration().callbackUri(), new AuthorizationCallback(
                request.parameters().get("state"), configuration().issuer().toString(),
                "owned-code-" + authorizations.size(), null, null));
        };
        TokenExchange token = request -> {
            exchanges.add(request);
            return new TokenResponse(200, Map.of("access_token", "private-access-" + exchanges.size(),
                "token_type", "Bearer", "scope", "tools:read"));
        };
        OAuthFlowEngine engine = engine(browser, token, new EmptyStore());
        for (var arm : List.of(
                new Arm(PkceChallenge.S256, VerifierSubmission.MATCHING),
                new Arm(PkceChallenge.S256, VerifierSubmission.MISMATCHED),
                new Arm(PkceChallenge.OMIT, VerifierSubmission.MATCHING),
                new Arm(PkceChallenge.OMIT, VerifierSubmission.OMIT))) {
            OwnedCodeExchangeResult result = engine.exchangeOwnedCode(configuration(), arm.challenge(), arm.verifier());
            require(result.status() == 200 && result.validTokenResponse() && !result.protectedOperationVerified(),
                "valid token response is recognized without asserting a protected effect");
            require(!result.toString().contains("private-access"), "result redacts token values");
        }
        require(authorizations.size() == 4 && exchanges.size() == 4, "one fresh code per arm");
        require(authorizations.stream().map(request -> request.parameters().get("state")).distinct().count() == 4,
            "each arm has an independent state");
        require(authorizations.get(0).parameters().containsKey("code_challenge"), "S256 advertises challenge");
        require(authorizations.get(1).parameters().containsKey("code_challenge"), "mismatched arm advertises challenge");
        require(challenge(exchanges.get(0).form().get("code_verifier")).equals(
            authorizations.get(0).parameters().get("code_challenge")), "matching verifier binds to S256 challenge");
        require(!challenge(exchanges.get(1).form().get("code_verifier")).equals(
            authorizations.get(1).parameters().get("code_challenge")), "mismatched verifier breaks S256 binding");
        require(!authorizations.get(2).parameters().containsKey("code_challenge")
            && !authorizations.get(2).parameters().containsKey("code_challenge_method"),
            "omitted challenge sends neither PKCE parameter");
        require(exchanges.get(2).form().containsKey("code_verifier")
            && !exchanges.get(3).form().containsKey("code_verifier"),
            "token verifier submission is independently selected");
        require(!exchanges.get(0).form().get("code_verifier").equals(exchanges.get(1).form().get("code_verifier")),
            "mismatched verifier differs from generated verifier");
        require(exchanges.stream().map(request -> request.form().get("code")).distinct().count() == 4,
            "each exchange uses its own owned code");
    }

    private static void invalidStateStopsBeforeTokenExchange() {
        BrowserAuthorization wrongState = request -> new BrowserCallback(configuration().callbackUri(),
            new AuthorizationCallback("wrong-state", configuration().issuer().toString(), "owned-code", null, null));
        OAuthFlowEngine engine = engine(wrongState, request -> {
            throw new AssertionError("invalid state must not reach token exchange");
        }, new EmptyStore());
        try {
            engine.exchangeOwnedCode(configuration(), PkceChallenge.OMIT, VerifierSubmission.MATCHING);
            throw new AssertionError("invalid callback must fail");
        } catch (OAuthFlowException expected) {
            require(expected.reason() == OAuthFlowException.Reason.INVALID_CALLBACK,
                "state mismatch is an invalid callback");
        }
    }

    private static void invalidTokenResponseIsRedactedAndNotSaved() {
        BrowserAuthorization browser = request -> new BrowserCallback(configuration().callbackUri(),
            new AuthorizationCallback(request.parameters().get("state"), configuration().issuer().toString(),
                "owned-code", null, null));
        OAuthFlowEngine engine = engine(browser,
            request -> new TokenResponse(400, Map.of("error", "invalid_request", "private", "secret-token")),
            new EmptyStore());
        OwnedCodeExchangeResult result = engine.exchangeOwnedCode(configuration(), PkceChallenge.OMIT,
            VerifierSubmission.MATCHING);
        require(result.status() == 400 && !result.validTokenResponse(), "rejected exchange is a negative arm");
        require(!result.toString().contains("secret-token"), "OAuth error fields are not exported");
    }

    private static void protectedOperationVerifierReceivesTokenOnlyAfterValidation() {
        BrowserAuthorization browser = request -> new BrowserCallback(configuration().callbackUri(),
            new AuthorizationCallback(request.parameters().get("state"), configuration().issuer().toString(),
                "owned-code", null, null));
        OAuthFlowEngine valid = engine(browser,
            request -> new TokenResponse(200, Map.of("access_token", "private-access", "token_type", "Bearer")),
            new EmptyStore());
        OwnedCodeExchangeResult proved = valid.exchangeOwnedCode(configuration(), PkceChallenge.OMIT,
            VerifierSubmission.MATCHING, token -> token.equals("private-access"));
        require(proved.validTokenResponse() && proved.protectedOperationVerified(),
            "protected verifier can consume the transient access token");
        require(!proved.toString().contains("private-access"), "result never contains the token");
        OAuthFlowEngine invalid = engine(browser,
            request -> new TokenResponse(200, Map.of("access_token", "", "token_type", "Bearer")),
            new EmptyStore());
        OwnedCodeExchangeResult rejected = invalid.exchangeOwnedCode(configuration(), PkceChallenge.OMIT,
            VerifierSubmission.MATCHING, token -> { throw new AssertionError("invalid token must not be verified"); });
        require(!rejected.validTokenResponse() && !rejected.protectedOperationVerified(),
            "invalid token never reaches the protected verifier");
    }

    private static void protectedOperationFailureDoesNotExportToken() {
        BrowserAuthorization browser = request -> new BrowserCallback(configuration().callbackUri(),
            new AuthorizationCallback(request.parameters().get("state"), configuration().issuer().toString(),
                "owned-code", null, null));
        OAuthFlowEngine engine = engine(browser,
            request -> new TokenResponse(200, Map.of("access_token", "private-access", "token_type", "Bearer")),
            new EmptyStore());
        try {
            engine.exchangeOwnedCode(configuration(), PkceChallenge.OMIT, VerifierSubmission.MATCHING,
                token -> { throw new IllegalStateException("verification failed for " + token); });
            throw new AssertionError("protected verifier failure must stop the arm");
        } catch (OAuthFlowException expected) {
            require(expected.reason() == OAuthFlowException.Reason.EXCHANGE_FAILED
                && !expected.getMessage().contains("private-access"),
                "protected verifier failure does not export its token-bearing message");
        }
    }

    private static void structuredOAuthDenialIsDistinctFromGenericHttpFailure() {
        BrowserAuthorization browser = request -> new BrowserCallback(configuration().callbackUri(),
            new AuthorizationCallback(request.parameters().get("state"), configuration().issuer().toString(),
                "owned-code", null, null));
        for (var response : List.of(
                new TokenResponse(400, Map.of("error", "invalid_request", "error_description", "private-code",
                    "access_token", "private-access", "client_secret", "private-secret")),
                new TokenResponse(400, Map.of()),
                new TokenResponse(429, Map.of("error", "invalid_request")),
                new TokenResponse(400, Map.of("error", "private-secret")))) {
            OwnedCodeExchangeResult result = engine(browser, request -> response, new EmptyStore())
                .exchangeOwnedCode(configuration(), PkceChallenge.OMIT, VerifierSubmission.MATCHING);
            require(result.oauthErrorCode().equals(response.fields().get("error") instanceof String error
                && error.equals("invalid_request") && response.status() == 400
                    ? Optional.of("invalid_request") : Optional.empty()),
                "only a complete structured OAuth denial is classified");
            require(!result.toString().contains("private-code") && !result.toString().contains("private-secret")
                && !result.toString().contains("private-access"),
                "result omits unrecognized fields and error descriptions");
        }
    }

    private static void incompleteTokenExchangeCannotClassifyDenial() {
        BrowserAuthorization browser = request -> new BrowserCallback(configuration().callbackUri(),
            new AuthorizationCallback(request.parameters().get("state"), configuration().issuer().toString(),
                "owned-code", null, null));
        OAuthFlowEngine engine = engine(browser,
            request -> { throw new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED,
                "truncated private-code private-secret"); }, new EmptyStore());
        try {
            engine.exchangeOwnedCode(configuration(), PkceChallenge.OMIT, VerifierSubmission.MATCHING);
            throw new AssertionError("incomplete token exchange cannot return a denial classification");
        } catch (OAuthFlowException expected) {
            require(expected.reason() == OAuthFlowException.Reason.EXCHANGE_FAILED
                && !expected.getMessage().contains("private-code")
                && !expected.getMessage().contains("private-secret"),
                "incomplete exchange fails without exporting credential-bearing details");
        }
    }

    private static OAuthFlowEngine engine(BrowserAuthorization browser, TokenExchange token, CredentialStore store) {
        return new OAuthFlowEngine(token, browser, store, Clock.fixed(
            Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
    }

    private static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static AuthorizationCodeConfig configuration() {
        URI issuer = URI.create("https://issuer.test");
        return new AuthorizationCodeConfig(issuer, URI.create("https://resource.test/mcp"),
            URI.create("https://issuer.test/authorize"), URI.create("https://issuer.test/token"),
            new RegisteredClient(issuer, "owned-client", null), URI.create("http://127.0.0.1:49151/callback"),
            Set.of("tools:read"), true);
    }

    private record Arm(PkceChallenge challenge, VerifierSubmission verifier) {}

    private static final class EmptyStore implements CredentialStore {
        public Optional<OAuthCredential> load(CredentialBinding binding) { return Optional.empty(); }
        public void save(CredentialBinding binding, OAuthCredential credential) {
            throw new AssertionError("probe must not persist tokens");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
