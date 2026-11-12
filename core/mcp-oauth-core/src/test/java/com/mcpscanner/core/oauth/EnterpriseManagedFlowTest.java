package com.mcpscanner.core.oauth;

import java.net.URI;
import java.time.*;
import java.util.*;

public final class EnterpriseManagedFlowTest {
    private static final URI ISSUER = URI.create("https://as.example/");
    private static final URI RESOURCE = URI.create("https://mcp.example/");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);

    public static void main(String[] args) {
        deniedIdentityStopsBeforeResourceExchange();
        malformedIdentityStopsBeforeResourceExchange();
        invalidConfigurationCannotUseCachedOrNewCredential();
        adapterFailuresAreSanitized();
        expiryRenewsBothHopsWithoutRefreshOrImplicitResource();
        basicAndJwtUseAdvertisedAuthentication();
        deniedResourceResponseNeverReplacesCredential();
        acceptsAndReusesDifferentGrantedScopes();
        System.out.println("EnterpriseManagedFlowTest passed");
    }

    private static void deniedIdentityStopsBeforeResourceExchange() {
        var store = new MemoryStore();
        var flow = new EnterpriseManagedFlow(request -> new TokenResponse(400, Map.of("error", "access_denied", "error_description", "secret-policy-detail")),
                request -> { throw new AssertionError("Resource AS contacted after IdP denial"); }, store, CLOCK);
        fails(() -> flow.authenticate(config()), OAuthFlowException.Reason.AUTHORIZATION_DENIED);
        check(store.saved.isEmpty(), "Denied grant cannot persist credentials");
    }

    private static void malformedIdentityStopsBeforeResourceExchange() {
        var valid = new HashMap<String, Object>(Map.of("issued_token_type", "urn:ietf:params:oauth:token-type:id-jag",
                "access_token", "id-jag", "token_type", "N_A", "expires_in", 300));
        for (var mutation : List.of(Map.of("issued_token_type", (Object) "urn:ietf:params:oauth:token-type:access_token"),
                Map.of("token_type", (Object) "Bearer"), Map.of("access_token", (Object) "bad\r\ntoken"),
                Map.of("expires_in", (Object) (-1)), Map.of("scope", (Object) 42))) {
            var fields = new HashMap<>(valid);
            fields.putAll(mutation);
            var flow = new EnterpriseManagedFlow(request -> new TokenResponse(200, fields),
                    request -> { throw new AssertionError("Resource AS contacted with invalid ID-JAG"); }, new MemoryStore(), CLOCK);
            fails(() -> flow.authenticate(config()), OAuthFlowException.Reason.INVALID_TOKEN_RESPONSE);
        }
    }

    private static void invalidConfigurationCannotUseCachedOrNewCredential() {
        var base = config();
        var other = URI.create("https://other.example/");
        var invalid = new ArrayList<EnterpriseManagedConfig>();
        invalid.add(null);
        invalid.add(new EnterpriseManagedConfig(base.binding(), base.resourceMetadata(), null, base.client(), base.authentication(), true));
        invalid.add(new EnterpriseManagedConfig(base.binding(), base.resourceMetadata(),
                new EnterpriseAuthorizationMetadata(ISSUER, ISSUER.resolve("token"), Set.of(), Set.of("none"), Set.of()), base.client(), base.authentication(), true));
        invalid.add(new EnterpriseManagedConfig(base.binding(), new ProtectedResourceMetadata(other, List.of(ISSUER), Set.of()), base.serverMetadata(), base.client(), base.authentication(), true));
        invalid.add(new EnterpriseManagedConfig(base.binding(), new ProtectedResourceMetadata(RESOURCE, List.of(other), Set.of()), base.serverMetadata(), base.client(), base.authentication(), true));
        invalid.add(new EnterpriseManagedConfig(base.binding(), base.resourceMetadata(), base.serverMetadata(), new RegisteredClient(other, "scanner", null), base.authentication(), true));
        invalid.add(new EnterpriseManagedConfig(new CredentialBinding(ISSUER, RESOURCE, "scanner", Set.of("read")), base.resourceMetadata(), base.serverMetadata(), base.client(), base.authentication(), true));
        invalid.add(new EnterpriseManagedConfig(base.binding(), base.resourceMetadata(),
                new EnterpriseAuthorizationMetadata(other, other.resolve("token"), base.serverMetadata().grantProfiles(), Set.of("none"), Set.of()), base.client(), base.authentication(), true));
        invalid.add(new EnterpriseManagedConfig(base.binding(), base.resourceMetadata(), base.serverMetadata(), new RegisteredClient(ISSUER, "other-client", null), base.authentication(), true));
        invalid.add(new EnterpriseManagedConfig(base.binding(), base.resourceMetadata(), base.serverMetadata(), base.client(), ClientCredentialsAuthentication.basic(), true));
        var store = new MemoryStore();
        store.save(base.binding(), new OAuthCredential(base.binding(), "cached", null, CLOCK.instant().plusSeconds(300), Set.of("read")));
        var flow = new EnterpriseManagedFlow(request -> { throw new AssertionError("IdP contacted for invalid config"); },
                request -> { throw new AssertionError("AS contacted for invalid config"); }, store, CLOCK);
        for (var value : invalid) {
            fails(() -> flow.credential(value), OAuthFlowException.Reason.INVALID_CONFIGURATION);
            fails(() -> flow.authenticate(value), OAuthFlowException.Reason.INVALID_CONFIGURATION);
        }
    }

    private static void adapterFailuresAreSanitized() {
        var failure = new IllegalStateException("secret-token-and-identity");
        fails(() -> new EnterpriseManagedFlow(request -> { throw failure; }, request -> accessToken(), new MemoryStore(), CLOCK)
                .authenticate(config()), OAuthFlowException.Reason.EXCHANGE_FAILED);
        fails(() -> new EnterpriseManagedFlow(request -> identityToken(), request -> { throw failure; }, new MemoryStore(), CLOCK)
                .authenticate(config()), OAuthFlowException.Reason.EXCHANGE_FAILED);
        CredentialStore broken = new CredentialStore() {
            public Optional<OAuthCredential> load(CredentialBinding binding) { throw failure; }
            public void save(CredentialBinding binding, OAuthCredential credential) { throw failure; }
        };
        var flow = new EnterpriseManagedFlow(request -> identityToken(), request -> accessToken(), broken, CLOCK);
        fails(() -> flow.credential(config()), OAuthFlowException.Reason.STORAGE_FAILED);
        fails(() -> flow.authenticate(config()), OAuthFlowException.Reason.STORAGE_FAILED);
    }

    private static TokenResponse identityToken() {
        return new TokenResponse(200, Map.of("issued_token_type", "urn:ietf:params:oauth:token-type:id-jag",
                "access_token", "identity-jag", "token_type", "N_A", "expires_in", 300));
    }

    private static TokenResponse accessToken() {
        return new TokenResponse(200, Map.of("access_token", "mcp-access", "token_type", "Bearer", "expires_in", 300, "refresh_token", "discarded"));
    }

    private static void expiryRenewsBothHopsWithoutRefreshOrImplicitResource() {
        var base = config();
        var config = new EnterpriseManagedConfig(base.binding(), base.resourceMetadata(), base.serverMetadata(), base.client(), base.authentication(), false);
        var store = new MemoryStore();
        store.save(config.binding(), new OAuthCredential(config.binding(), "old", "old-refresh", CLOCK.instant().plusSeconds(29), Set.of("read")));
        var hops = new ArrayList<String>();
        var flow = new EnterpriseManagedFlow(request -> {
            check(!request.form().containsKey("resource"), "Optional resource omitted intentionally");
            check(request.form().get("audience").equals(ISSUER.toString()), "Audience always AS issuer");
            hops.add("idp");
            return identityToken();
        }, request -> {
            check(request.form().get("grant_type").equals("urn:ietf:params:oauth:grant-type:jwt-bearer"), "Renewal repeats enterprise grant");
            check(!request.form().containsKey("refresh_token"), "Renewal never uses refresh grant");
            hops.add("as");
            return accessToken();
        }, store, CLOCK);
        var value = flow.credential(config);
        check(hops.equals(List.of("idp", "as")) && value.refreshToken() == null, "Both hops renewed without refresh assumptions");
        flow.credential(config);
        check(hops.size() == 2, "Valid credential reused");
        var wrongGrant = new CredentialBinding(ISSUER, RESOURCE, "scanner", Set.of("read"), OAuthGrantMode.CLIENT_CREDENTIALS);
        store.save(config.binding(), new OAuthCredential(wrongGrant, "wrong-grant", null, CLOCK.instant().plusSeconds(300), Set.of("read")));
        flow.credential(config);
        check(hops.size() == 4, "Stored grant mismatch reacquires enterprise credential");
    }

    private static void basicAndJwtUseAdvertisedAuthentication() {
        var base = config();
        var metadata = new EnterpriseAuthorizationMetadata(ISSUER, ISSUER.resolve("token"), base.serverMetadata().grantProfiles(),
                Set.of("client_secret_basic", "private_key_jwt"), Set.of("RS256"));
        var basic = new EnterpriseManagedConfig(base.binding(), base.resourceMetadata(), metadata,
                new RegisteredClient(ISSUER, "scanner", "secret password"), ClientCredentialsAuthentication.basic(), true);
        new EnterpriseManagedFlow(request -> identityToken(), request -> {
            check(request.headers().get("Authorization").equals("Basic c2Nhbm5lcjpzZWNyZXQrcGFzc3dvcmQ="), "Encoded Basic client credentials");
            check(!request.form().containsKey("client_secret") && !request.form().containsKey("client_assertion"), "Exactly one client auth method");
            return accessToken();
        }, new MemoryStore(), CLOCK).authenticate(basic);
        var signed = new ArrayList<String>();
        var jwt = new EnterpriseManagedConfig(base.binding(), base.resourceMetadata(), metadata, base.client(),
                ClientCredentialsAuthentication.privateKeyJwt("RS256", "scanner-key", (algorithm, input) -> {
                    check(algorithm.equals("RS256"), "Advertised signing algorithm");
                    var segments = new String(input, java.nio.charset.StandardCharsets.US_ASCII).split("\\.");
                    String claims = new String(Base64.getUrlDecoder().decode(segments[1]), java.nio.charset.StandardCharsets.UTF_8);
                    check(claims.contains("\"aud\":\"" + metadata.tokenEndpoint() + "\"") && claims.contains("\"iss\":\"scanner\""), "AS-bound assertion claims");
                    signed.add(claims);
                    return new byte[] { 1, 2, 3 };
                }), true);
        new EnterpriseManagedFlow(request -> identityToken(), request -> {
            check(request.form().get("client_assertion_type").equals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"), "JWT client assertion method");
            check(request.form().containsKey("assertion") && request.form().containsKey("client_assertion"), "Identity grant and client proof are distinct");
            check(!request.headers().containsKey("Authorization") && !request.form().containsKey("client_secret"), "One JWT authentication method");
            return accessToken();
        }, new MemoryStore(), CLOCK).authenticate(jwt);
        check(signed.size() == 1, "Injected signer used");
    }

    private static void deniedResourceResponseNeverReplacesCredential() {
        var config = config();
        var store = new MemoryStore();
        var old = new OAuthCredential(config.binding(), "existing", null, CLOCK.instant().plusSeconds(300), Set.of("read"));
        store.save(config.binding(), old);
        var flow = new EnterpriseManagedFlow(request -> identityToken(),
                request -> new TokenResponse(400, Map.of("error", "access_denied", "error_description", "secret-as-detail")), store, CLOCK);
        fails(() -> flow.authenticate(config), OAuthFlowException.Reason.INVALID_TOKEN_RESPONSE);
        check(store.saved.get(config.binding()).equals(old), "AS denial preserves prior credential");
    }

    private static void acceptsAndReusesDifferentGrantedScopes() {
        var identity = new HashMap<String, Object>(identityToken().fields());
        identity.put("scope", "other");
        var hops = new ArrayList<String>();
        var flow = new EnterpriseManagedFlow(request -> { hops.add("idp"); return new TokenResponse(200, identity); }, request -> {
            check(request.form().get("assertion").equals("identity-jag"), "Differently scoped ID-JAG presented");
            hops.add("as");
            return new TokenResponse(200, Map.of("access_token", "mcp-access", "token_type", "Bearer", "expires_in", 300, "scope", "narrow"));
        }, new MemoryStore(), CLOCK);
        check(flow.credential(config()).grantedScopes().equals(Set.of("narrow")), "Different resource grant stored as returned");
        flow.credential(config());
        check(hops.equals(List.of("idp", "as")), "Valid differently scoped credential reused");
    }

    private static EnterpriseManagedConfig config() {
        var binding = new CredentialBinding(ISSUER, RESOURCE, "scanner", Set.of("read"), OAuthGrantMode.ENTERPRISE_MANAGED);
        return new EnterpriseManagedConfig(binding, new ProtectedResourceMetadata(RESOURCE, List.of(ISSUER), binding.scopes()),
                new EnterpriseAuthorizationMetadata(ISSUER, ISSUER.resolve("token"), Set.of("urn:ietf:params:oauth:grant-profile:id-jag"), Set.of("none"), Set.of()),
                new RegisteredClient(ISSUER, "scanner", null), ClientCredentialsAuthentication.publicClient(), true);
    }

    private static void fails(Runnable action, OAuthFlowException.Reason reason) {
        try { action.run(); throw new AssertionError("Expected " + reason); }
        catch (OAuthFlowException failure) {
            check(failure.reason() == reason, "Expected typed " + reason + ", got " + failure.reason());
            check(!failure.toString().contains("secret") && failure.getCause() == null, "Sanitized failure");
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class MemoryStore implements CredentialStore {
        final Map<CredentialBinding, OAuthCredential> saved = new HashMap<>();
        public Optional<OAuthCredential> load(CredentialBinding binding) { return Optional.ofNullable(saved.get(binding)); }
        public void save(CredentialBinding binding, OAuthCredential credential) { saved.put(binding, credential); }
    }
}
