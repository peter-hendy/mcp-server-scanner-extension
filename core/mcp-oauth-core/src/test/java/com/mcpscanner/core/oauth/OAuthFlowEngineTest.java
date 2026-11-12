package com.mcpscanner.core.oauth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class OAuthFlowEngineTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    public static void main(String[] args) throws Exception {
        exchangesAuthorizationCodeWithBoundPkceAndStoresCredential();
        rejectsCallbackAtDifferentRedirectBeforeTokenExchange();
        rejectsUnusableTokenResponsesWithoutPersistingThem();
        refreshesBoundCredentialAndPersistsRefreshTokenRotation();
        rejectsWrongIssuerConfigurationBeforeBrowserOrExchange();
        rejectsDeniedOrEmptyAuthorizationBeforeExchange();
        authenticatesConfidentialClientWithBasicHeader();
        authenticatesAndRefreshesWithClientSecretPost();
        rejectsInvalidTokenAuthenticationBeforeAdaptersRun();
        preservesDefaultTokenAuthenticationMethods();
        rejectsUnsafeLocationsBeforeAdaptersRun();
        redactsSensitiveValuesAndAdapterFailures();
        isolatesCredentialBindingsAndSanitizesBrowserAndStorageFailures();
        preservesExactUriIdentityInCallbackAndCredentialBinding();
        preservesGrantedScopesWhenRefreshResponseOmitsScope();
        storesGrantedScopesAsReturned();
        acceptsAnyLoopbackAddressForLocalServers();
        System.out.println("OAuthFlowEngineTest passed");
    }

    private static void exchangesAuthorizationCodeWithBoundPkceAndStoresCredential() throws Exception {
        var store = new MemoryStore();
        var config = configuration();
        var authorization = new HashMap<String, String>();
        BrowserAuthorization browser = request -> {
            authorization.putAll(request.parameters());
            check(request.endpoint().equals(config.authorizationEndpoint()), "Authorization endpoint is bound");
            return new BrowserCallback(config.callbackUri(), new AuthorizationCallback(
                    request.parameters().get("state"), config.issuer().toString(), "authorization-secret", null, null));
        };
        TokenExchange exchange = request -> {
            check(request.endpoint().equals(config.tokenEndpoint()), "Token endpoint is bound");
            check(request.form().get("grant_type").equals("authorization_code"), "Authorization code grant");
            check(request.form().get("code").equals("authorization-secret"), "Validated code exchanged");
            check(request.form().get("redirect_uri").equals(config.callbackUri().toString()), "Exact callback sent");
            check(request.form().get("resource").equals(config.resource().toString()), "Resource is bound");
            check(request.form().get("client_id").equals(config.client().clientId()), "Public client identified in the form");
            check(authorization.get("code_challenge_method").equals("S256"), "PKCE S256 required");
            try {
                String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(request.form().get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
                check(challenge.equals(authorization.get("code_challenge")), "PKCE proof matches authorization");
            } catch (Exception e) { throw new AssertionError(e); }
            return new TokenResponse(200, Map.of("access_token", "access-secret", "refresh_token", "refresh-secret",
                    "token_type", "Bearer", "expires_in", 300, "scope", "tools:read"));
        };
        var credential = new OAuthFlowEngine(exchange, browser, store, Clock.fixed(NOW, ZoneOffset.UTC)).authenticate(config);
        check(credential.accessToken().equals("access-secret"), "Access token returned");
        check(credential.expiresAt().equals(NOW.plusSeconds(300)), "Injected clock determines expiration");
        check(store.load(config.binding()).orElseThrow().equals(credential), "Bound credential persisted");
        check(authorization.get("resource").equals(config.resource().toString()), "Authorization resource bound");
        check(authorization.get("scope").equals("tools:read"), "Scopes requested");
    }

    private static void rejectsCallbackAtDifferentRedirectBeforeTokenExchange() {
        var config = configuration();
        var store = new MemoryStore();
        BrowserAuthorization browser = request -> new BrowserCallback(URI.create("http://127.0.0.1:9999/callback"),
                new AuthorizationCallback(request.parameters().get("state"), config.issuer().toString(), "code", null, null));
        var engine = new OAuthFlowEngine(request -> { throw new AssertionError("Invalid callback must not exchange token"); },
                browser, store, Clock.fixed(NOW, ZoneOffset.UTC));
        expectFailure(OAuthFlowException.Reason.INVALID_CALLBACK, () -> engine.authenticate(config));
        check(store.load(config.binding()).isEmpty(), "Invalid callback cannot store tokens");
    }

    private static void expectFailure(OAuthFlowException.Reason reason, Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection: " + reason); }
        catch (OAuthFlowException failure) { check(failure.reason() == reason, "Expected failure reason: " + reason); }
    }

    private static void rejectsUnusableTokenResponsesWithoutPersistingThem() {
        var invalidResponses = java.util.List.of(
                new TokenResponse(400, Map.of("error", "invalid_grant", "error_description", "server-secret")),
                new TokenResponse(200, Map.of("access_token", "", "token_type", "Bearer", "expires_in", 300)),
                new TokenResponse(200, Map.of("access_token", "access-secret", "token_type", "MAC", "expires_in", 300)),
                new TokenResponse(200, Map.of("access_token", "access-secret", "token_type", "Bearer", "expires_in", -1)),
                new TokenResponse(200, Map.of("access_token", "access-secret", "token_type", "Bearer", "expires_in", 1.5)),
                new TokenResponse(200, Map.of("access_token", "access-secret", "token_type", "Bearer", "expires_in", "bad")),
                new TokenResponse(200, Map.of("access_token", "access-secret", "token_type", "Bearer", "expires_in", 300, "scope", 42)),
                new TokenResponse(200, Map.of("access_token", "access-secret", "token_type", "Bearer", "expires_in", 300, "scope", "tools:read bad\"scope")),
                new TokenResponse(200, Map.of("access_token", "access\r\nsecret", "token_type", "Bearer", "expires_in", 300)),
                new TokenResponse(200, Map.of("access_token", "access-secret", "token_type", "Bearer", "expires_in", 300, "refresh_token", 42)));
        for (var response : invalidResponses) {
            var store = new MemoryStore();
            var engine = new OAuthFlowEngine(request -> response, validBrowser(), store, Clock.fixed(NOW, ZoneOffset.UTC));
            expectFailure(OAuthFlowException.Reason.INVALID_TOKEN_RESPONSE, () -> engine.authenticate(configuration()));
            check(store.load(configuration().binding()).isEmpty(), "Invalid response is not persisted");
        }
    }

    private static BrowserAuthorization validBrowser() {
        return request -> new BrowserCallback(configuration().callbackUri(), new AuthorizationCallback(
                request.parameters().get("state"), configuration().issuer().toString(), "authorization-secret", null, null));
    }

    private static void refreshesBoundCredentialAndPersistsRefreshTokenRotation() {
        var config = configuration();
        var store = new MemoryStore();
        store.save(config.binding(), new OAuthCredential(config.binding(), "old-access", "old-refresh", NOW, config.scopes()));
        TokenExchange exchange = request -> {
            check(request.form().get("grant_type").equals("refresh_token"), "Refresh grant");
            check(request.form().get("refresh_token").equals("old-refresh"), "Stored refresh token used");
            check(request.form().get("resource").equals(config.resource().toString()), "Refresh is resource bound");
            check(request.form().get("client_id").equals(config.client().clientId()), "Refresh is client bound");
            check(!request.form().containsKey("code_verifier"), "PKCE verifier is not reused for refresh");
            check(request.method().equals("POST"), "Grant HTTP method");
            check(request.headers().get("Content-Type").equals("application/x-www-form-urlencoded"), "Form wire encoding");
            check(request.body().contains("grant_type=refresh_token"), "Engine builds form body");
            return new TokenResponse(200, Map.of("access_token", "new-access", "refresh_token", "rotated-refresh", "token_type", "bearer", "expires_in", "600"));
        };
        var engine = new OAuthFlowEngine(exchange, request -> { throw new AssertionError("Refresh must not open browser"); }, store, Clock.fixed(NOW, ZoneOffset.UTC));
        var credential = engine.refresh(config);
        check(credential.refreshToken().equals("rotated-refresh"), "Refresh token rotates");
        check(credential.accessToken().equals("new-access"), "Access token replaces old token");
        check(credential.expiresAt().equals(NOW.plusSeconds(600)), "Refreshed expiry");
        check(store.load(config.binding()).orElseThrow().equals(credential), "Rotated token replaces stored token");
        var retainingEngine = new OAuthFlowEngine(request -> new TokenResponse(200, Map.of("access_token", "latest-access", "token_type", "Bearer")),
                validBrowser(), store, Clock.fixed(NOW, ZoneOffset.UTC));
        check(retainingEngine.refresh(config).refreshToken().equals("rotated-refresh"), "Omitted refresh token preserves latest token");
    }

    private static void rejectsWrongIssuerConfigurationBeforeBrowserOrExchange() {
        var good = configuration();
        var wrong = new AuthorizationCodeConfig(good.issuer(), good.resource(), good.authorizationEndpoint(), good.tokenEndpoint(),
                new RegisteredClient(URI.create("https://other.example"), "scanner-client", null), good.callbackUri(), good.scopes(), true);
        var engine = new OAuthFlowEngine(request -> { throw new AssertionError("Invalid config must not exchange"); },
                request -> { throw new AssertionError("Invalid config must not open browser"); }, new MemoryStore(), Clock.fixed(NOW, ZoneOffset.UTC));
        expectFailure(OAuthFlowException.Reason.INVALID_CONFIGURATION, () -> engine.authenticate(wrong));
        expectFailure(OAuthFlowException.Reason.INVALID_CONFIGURATION, () -> engine.refresh(wrong));
    }

    private static void rejectsDeniedOrEmptyAuthorizationBeforeExchange() {
        for (boolean denied : new boolean[] {true, false}) {
            BrowserAuthorization browser = request -> new BrowserCallback(configuration().callbackUri(), new AuthorizationCallback(
                    request.parameters().get("state"), configuration().issuer().toString(), denied ? "code" : "", denied ? "access_denied" : null, "server-secret"));
            var engine = new OAuthFlowEngine(request -> { throw new AssertionError("Denied callback must not exchange"); },
                    browser, new MemoryStore(), Clock.fixed(NOW, ZoneOffset.UTC));
            expectFailure(denied ? OAuthFlowException.Reason.AUTHORIZATION_DENIED : OAuthFlowException.Reason.INVALID_CALLBACK,
                    () -> engine.authenticate(configuration()));
        }
    }

    private static void authenticatesConfidentialClientWithBasicHeader() {
        var publicConfig = configuration();
        var config = new AuthorizationCodeConfig(publicConfig.issuer(), publicConfig.resource(), publicConfig.authorizationEndpoint(),
                publicConfig.tokenEndpoint(), new RegisteredClient(publicConfig.issuer(), "scanner:client", "client secret"),
                publicConfig.callbackUri(), publicConfig.scopes(), true);
        TokenExchange exchange = request -> {
            String decoded = new String(Base64.getDecoder().decode(request.headers().get("Authorization").substring(6)), StandardCharsets.UTF_8);
            check(decoded.equals("scanner%3Aclient:client+secret"), "OAuth Basic components are form encoded");
            check(!request.form().containsKey("client_secret"), "Client secret only sent through chosen authentication method");
            check(!request.form().containsKey("client_id"), "Basic client identifies itself only in the Authorization header");
            return new TokenResponse(200, Map.of("access_token", "access-secret", "refresh_token", "refresh-secret",
                    "token_type", "Bearer"));
        };
        var engine = new OAuthFlowEngine(exchange, validBrowser(), new MemoryStore(), Clock.fixed(NOW, ZoneOffset.UTC));
        check(engine.authenticate(config).accessToken().equals("access-secret"), "Confidential client obtains credential");
        check(engine.refresh(config).accessToken().equals("access-secret"), "Confidential client refreshes credential");
    }

    private static void authenticatesAndRefreshesWithClientSecretPost() {
        var base = configuration();
        var config = new AuthorizationCodeConfig(base.issuer(), base.resource(), base.authorizationEndpoint(),
                base.tokenEndpoint(), new RegisteredClient(base.issuer(), "scanner:client", "client secret&value"),
                base.callbackUri(), base.scopes(), true, "client_secret_post");
        var store = new MemoryStore();
        TokenExchange strictEndpoint = request -> {
            check(!request.headers().containsKey("Authorization"), "POST client authentication has no Basic header");
            check(request.form().get("client_id").equals("scanner:client"), "POST client identifier supplied");
            check(request.form().get("client_secret").equals("client secret&value"), "POST client secret supplied");
            check(request.form().get("resource").equals(config.resource().toString()), "POST grant remains resource bound");
            check(request.body().contains("client_secret=client+secret%26value"), "POST secret uses form encoding");
            boolean refresh = request.form().get("grant_type").equals("refresh_token");
            if (refresh) check(request.form().get("refresh_token").equals("initial-refresh"), "Bound refresh token supplied");
            else check(request.form().containsKey("code_verifier"), "Code exchange retains PKCE");
            return new TokenResponse(200, Map.of("access_token", refresh ? "rotated-access" : "initial-access",
                    "refresh_token", refresh ? "rotated-refresh" : "initial-refresh", "token_type", "Bearer"));
        };
        var engine = new OAuthFlowEngine(strictEndpoint, validBrowser(), store, Clock.fixed(NOW, ZoneOffset.UTC));
        check(engine.authenticate(config).accessToken().equals("initial-access"), "POST client completes code flow");
        check(engine.refresh(config).refreshToken().equals("rotated-refresh"), "POST client refresh rotates credential");
        check(store.load(config.binding()).orElseThrow().accessToken().equals("rotated-access"), "Rotated POST token persisted");
    }

    private static void rejectsInvalidTokenAuthenticationBeforeAdaptersRun() {
        var base = configuration();
        String[][] invalid = {{"client_secret_post", null}, {"client_secret_basic", " "},
                {"none", "client-secret"}, {"unsupported-secret", "client-secret"}, {null, "client-secret"}};
        var engine = new OAuthFlowEngine(request -> { throw new AssertionError("Invalid authentication must not exchange"); },
                request -> { throw new AssertionError("Invalid authentication must not open browser"); },
                new MemoryStore(), Clock.fixed(NOW, ZoneOffset.UTC));
        for (var authentication : invalid) {
            var config = new AuthorizationCodeConfig(base.issuer(), base.resource(), base.authorizationEndpoint(),
                    base.tokenEndpoint(), new RegisteredClient(base.issuer(), "scanner-client", authentication[1]),
                    base.callbackUri(), base.scopes(), true, authentication[0]);
            expectSafeFailure(OAuthFlowException.Reason.INVALID_CONFIGURATION, () -> engine.authenticate(config));
            expectSafeFailure(OAuthFlowException.Reason.INVALID_CONFIGURATION, () -> engine.refresh(config));
            check(!config.toString().contains("secret"), "Authentication configuration diagnostics redact method and secret");
        }
    }

    private static void preservesDefaultTokenAuthenticationMethods() {
        var base = configuration();
        check(base.tokenEndpointAuthMethod().equals("none"), "Old public constructor defaults to none");
        var confidential = new AuthorizationCodeConfig(base.issuer(), base.resource(), base.authorizationEndpoint(),
                base.tokenEndpoint(), new RegisteredClient(base.issuer(), "scanner-client", "client-secret"),
                base.callbackUri(), base.scopes(), true);
        check(confidential.tokenEndpointAuthMethod().equals("client_secret_basic"), "Old confidential constructor defaults to Basic");
        var store = new MemoryStore();
        TokenExchange endpoint = request -> {
            check(!request.form().containsKey("client_secret"), "Default authentication never adds a form secret");
            if (request.form().get("grant_type").equals("refresh_token"))
                check(request.headers().containsKey("Authorization"), "Default confidential refresh uses Basic");
            else check(!request.headers().containsKey("Authorization"), "Default public exchange is unauthenticated");
            return new TokenResponse(200, Map.of("access_token", "access", "refresh_token", "refresh", "token_type", "Bearer"));
        };
        var engine = new OAuthFlowEngine(endpoint, validBrowser(), store, Clock.fixed(NOW, ZoneOffset.UTC));
        engine.authenticate(base);
        store.save(confidential.binding(), new OAuthCredential(confidential.binding(), "old", "refresh", NOW, confidential.scopes()));
        engine.refresh(confidential);
    }

    private static void rejectsUnsafeLocationsBeforeAdaptersRun() {
        var good = configuration();
        var invalid = java.util.List.of(
                new AuthorizationCodeConfig(good.issuer(), good.resource(), good.authorizationEndpoint(), URI.create("http://issuer.example/token"), good.client(), good.callbackUri(), good.scopes(), true),
                new AuthorizationCodeConfig(good.issuer(), URI.create("https://user:secret@mcp.example/mcp"), good.authorizationEndpoint(), good.tokenEndpoint(), good.client(), good.callbackUri(), good.scopes(), true),
                new AuthorizationCodeConfig(good.issuer(), good.resource(), good.authorizationEndpoint(), good.tokenEndpoint(), good.client(), URI.create("http://attacker.example/callback"), good.scopes(), true),
                new AuthorizationCodeConfig(good.issuer(), good.resource(), good.authorizationEndpoint(), good.tokenEndpoint(), good.client(), good.callbackUri(), Set.of("scope one"), true));
        var engine = new OAuthFlowEngine(request -> { throw new AssertionError("Unsafe endpoint must not receive grant"); },
                request -> { throw new AssertionError("Unsafe endpoint must not open browser"); }, new MemoryStore(), Clock.fixed(NOW, ZoneOffset.UTC));
        for (var config : invalid) expectFailure(OAuthFlowException.Reason.INVALID_CONFIGURATION, () -> engine.authenticate(config));
    }

    private static void redactsSensitiveValuesAndAdapterFailures() {
        var callback = new AuthorizationCallback("state-secret", "https://issuer.example", "code-secret", "denied", "description-secret");
        var values = java.util.List.of(callback, new BrowserCallback(configuration().callbackUri(), callback),
                new AuthorizationRequest(configuration().authorizationEndpoint(), Map.of("state", "state-secret")),
                new TokenRequest(configuration().tokenEndpoint(), Map.of("refresh_token", "refresh-secret"), Map.of("Authorization", "client-secret")),
                new TokenResponse(400, Map.of("error_description", "description-secret")),
                new OAuthCredential(configuration().binding(), "access-secret", "refresh-secret", NOW, Set.of("tools:read")));
        for (var value : values) check(!value.toString().contains("secret"), "Diagnostic representation redacts sensitive fields");
        var engine = new OAuthFlowEngine(request -> { throw new IllegalStateException("response-secret"); }, validBrowser(), new MemoryStore(), Clock.fixed(NOW, ZoneOffset.UTC));
        try {
            engine.authenticate(configuration());
            throw new AssertionError("Failed adapter must not succeed");
        } catch (OAuthFlowException failure) {
            check(failure.reason() == OAuthFlowException.Reason.EXCHANGE_FAILED, "Typed exchange failure");
            check(!failure.toString().contains("secret") && failure.getCause() == null, "Adapter failure remains secret safe");
        }
    }

    private static void isolatesCredentialBindingsAndSanitizesBrowserAndStorageFailures() {
        var config = configuration();
        CredentialStore wrongStore = new CredentialStore() {
            public java.util.Optional<OAuthCredential> load(CredentialBinding binding) {
                var wrong = new CredentialBinding(binding.issuer(), URI.create("https://other-resource.example"), binding.clientId(), binding.scopes());
                return java.util.Optional.of(new OAuthCredential(wrong, "wrong-access", "wrong-refresh", NOW, wrong.scopes()));
            }
            public void save(CredentialBinding binding, OAuthCredential credential) { throw new AssertionError("Wrong token must not save"); }
        };
        var isolated = new OAuthFlowEngine(request -> { throw new AssertionError("Wrong bound token must not leave host"); }, validBrowser(), wrongStore, Clock.fixed(NOW, ZoneOffset.UTC));
        expectFailure(OAuthFlowException.Reason.MISSING_CREDENTIAL, () -> isolated.refresh(config));
        var browserFailure = new OAuthFlowEngine(request -> { throw new AssertionError("Browser failed"); },
                request -> { throw new IllegalStateException("browser-secret"); }, new MemoryStore(), Clock.fixed(NOW, ZoneOffset.UTC));
        expectSafeFailure(OAuthFlowException.Reason.AUTHORIZATION_FAILED, () -> browserFailure.authenticate(config));
        CredentialStore failedStore = new CredentialStore() {
            public java.util.Optional<OAuthCredential> load(CredentialBinding binding) { throw new IllegalStateException("load-secret"); }
            public void save(CredentialBinding binding, OAuthCredential credential) { throw new IllegalStateException("save-secret"); }
        };
        var storageFailure = new OAuthFlowEngine(request -> new TokenResponse(200, Map.of("access_token", "access-secret", "token_type", "Bearer")),
                validBrowser(), failedStore, Clock.fixed(NOW, ZoneOffset.UTC));
        expectSafeFailure(OAuthFlowException.Reason.STORAGE_FAILED, () -> storageFailure.authenticate(config));
        expectSafeFailure(OAuthFlowException.Reason.STORAGE_FAILED, () -> storageFailure.refresh(config));
    }

    private static void expectSafeFailure(OAuthFlowException.Reason reason, Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection: " + reason); }
        catch (OAuthFlowException failure) {
            check(failure.reason() == reason, "Typed adapter failure");
            check(!failure.toString().contains("secret") && failure.getCause() == null, "Adapter details remain redacted");
        }
    }

    private static void preservesExactUriIdentityInCallbackAndCredentialBinding() {
        var config = configuration();
        BrowserAuthorization browser = request -> new BrowserCallback(URI.create("HTTP://127.0.0.1:8765/callback"),
                new AuthorizationCallback(request.parameters().get("state"), config.issuer().toString(), "code", null, null));
        var engine = new OAuthFlowEngine(request -> { throw new AssertionError("Callback must match exact registered string"); },
                browser, new MemoryStore(), Clock.fixed(NOW, ZoneOffset.UTC));
        expectFailure(OAuthFlowException.Reason.INVALID_CALLBACK, () -> engine.authenticate(config));
        var differentIssuer = new CredentialBinding(URI.create("https://ISSUER.example"), config.resource(), config.client().clientId(), config.scopes());
        check(!differentIssuer.equals(config.binding()), "Credential issuer identity is exact without URI normalization");
    }

    private static void preservesGrantedScopesWhenRefreshResponseOmitsScope() {
        var config = configuration();
        var store = new MemoryStore();
        var granted = Set.of("tools:read", "resources:read");
        store.save(config.binding(), new OAuthCredential(config.binding(), "old-access", "refresh-secret", NOW, granted));
        var engine = new OAuthFlowEngine(request -> new TokenResponse(200, Map.of("access_token", "new-access", "token_type", "Bearer")),
                validBrowser(), store, Clock.fixed(NOW, ZoneOffset.UTC));
        check(engine.refresh(config).grantedScopes().equals(granted), "Omitted refresh scope retains actual previous grant");
    }

    private static void storesGrantedScopesAsReturned() {
        var grants = Map.of("other", Set.of("other"), "  tools:read   extra ", Set.of("tools:read", "extra"), " ", Set.<String>of());
        for (var grant : grants.entrySet()) {
            var store = new MemoryStore();
            var engine = new OAuthFlowEngine(request -> new TokenResponse(200, Map.of("access_token", "access-secret",
                    "token_type", "Bearer", "scope", grant.getKey())), validBrowser(), store, Clock.fixed(NOW, ZoneOffset.UTC));
            check(engine.authenticate(configuration()).grantedScopes().equals(grant.getValue()), "Granted scope stored as returned: " + grant.getKey());
            check(store.load(configuration().binding()).orElseThrow().grantedScopes().equals(grant.getValue()), "Returned grant persisted");
        }
    }

    private static void acceptsAnyLoopbackAddressForLocalServers() {
        URI issuer = URI.create("http://127.0.0.2:9000");
        var config = new AuthorizationCodeConfig(issuer, URI.create("http://localhost:3000/mcp"),
                URI.create("http://127.0.0.2:9000/authorize"), URI.create("http://127.0.0.2:9000/token"),
                new RegisteredClient(issuer, "scanner-client", null), URI.create("http://127.0.0.1:8765/callback"), Set.of("tools:read"), false);
        BrowserAuthorization browser = request -> new BrowserCallback(config.callbackUri(),
                new AuthorizationCallback(request.parameters().get("state"), null, "code", null, null));
        var engine = new OAuthFlowEngine(request -> new TokenResponse(200, Map.of("access_token", "local", "token_type", "Bearer")),
                browser, new MemoryStore(), Clock.fixed(NOW, ZoneOffset.UTC));
        check(engine.authenticate(config).accessToken().equals("local"), "Loopback authorization server accepted");
    }

    private static AuthorizationCodeConfig configuration() {
        URI issuer = URI.create("https://issuer.example");
        return new AuthorizationCodeConfig(issuer, URI.create("https://mcp.example/mcp"),
                URI.create("https://issuer.example/authorize"), URI.create("https://issuer.example/token"),
                new RegisteredClient(issuer, "scanner-client", null), URI.create("http://127.0.0.1:8765/callback"),
                Set.of("tools:read"), true);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class MemoryStore implements CredentialStore {
        private final Map<CredentialBinding, OAuthCredential> credentials = new HashMap<>();
        public java.util.Optional<OAuthCredential> load(CredentialBinding binding) { return java.util.Optional.ofNullable(credentials.get(binding)); }
        public void save(CredentialBinding binding, OAuthCredential credential) { credentials.put(binding, credential); }
    }
}
