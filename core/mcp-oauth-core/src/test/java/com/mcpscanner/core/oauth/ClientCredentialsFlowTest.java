package com.mcpscanner.core.oauth;

import java.net.URI;
import java.time.*;
import java.util.*;

public final class ClientCredentialsFlowTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    public static void main(String[] args) {
        rejectsUnboundOrUnadvertisedConfiguration();
        renewsExpiredCredentialsWithoutRefreshTokens();
        constructsBoundSignedAssertionsThroughSignerPort();
        sendsClientSecretPostCredentialsAsFormParameters();
        sanitizesDenialsAndAdapterFailures();
        rejectsUnsupportedSigningAndEmptySignatures();
        rejectsRetryStateFromAnotherGrantMode();
        reusesCredentialWithNarrowerGrant();
        acceptsLocalhostAuthorizationServer();
        System.out.println("ClientCredentialsFlowTest passed");
    }

    private static void rejectsUnboundOrUnadvertisedConfiguration() {
        var good = config();
        var wrongIssuer = URI.create("https://wrong-issuer.example");
        var wrongResource = URI.create("https://wrong-resource.example");
        var invalid = List.of(
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), metadata(wrongIssuer, Set.of("client_credentials"), Set.of("client_secret_basic")), good.client(), good.authentication()),
                new ClientCredentialsConfig(good.binding(), new ProtectedResourceMetadata(wrongResource, List.of(good.binding().issuer()), Set.of()), good.serverMetadata(), good.client(), good.authentication()),
                new ClientCredentialsConfig(good.binding(), new ProtectedResourceMetadata(good.binding().resource(), List.of(wrongIssuer), Set.of()), good.serverMetadata(), good.client(), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), good.serverMetadata(), new RegisteredClient(wrongIssuer, good.client().clientId(), "secret"), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), good.serverMetadata(), new RegisteredClient(good.binding().issuer(), "other-client", "secret"), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), good.serverMetadata(), new RegisteredClient(good.binding().issuer(), good.client().clientId(), null), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), metadata(good.binding().issuer(), Set.of("authorization_code"), Set.of("client_secret_basic")), good.client(), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), metadata(good.binding().issuer(), Set.of("client_credentials"), Set.of("client_secret_post")), good.client(), good.authentication()),
                new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), new ClientCredentialsMetadata(good.binding().issuer(), URI.create("http://issuer.example/token"), Set.of("client_credentials"), Set.of("client_secret_basic"), Set.of()), good.client(), good.authentication()));
        var flow = new ClientCredentialsFlow(request -> { throw new AssertionError("Invalid configuration reached token endpoint"); }, new MemoryStore(), clock());
        for (var config : invalid) expect(OAuthFlowException.Reason.INVALID_CONFIGURATION, () -> flow.authenticate(config));
    }

    private static void renewsExpiredCredentialsWithoutRefreshTokens() {
        var config = config();
        var codeBinding = new CredentialBinding(config.binding().issuer(), config.binding().resource(), config.binding().clientId(), config.binding().scopes());
        check(codeBinding.grantMode() == OAuthGrantMode.AUTHORIZATION_CODE, "Legacy constructor preserves authorization-code mode");
        check(!codeBinding.equals(config.binding()), "Grant modes isolate otherwise identical bindings");
        var store = new MemoryStore();
        store.save(codeBinding, new OAuthCredential(codeBinding, "user-access", "user-refresh", NOW.plusSeconds(3600), codeBinding.scopes()));
        var current = new Instant[] {NOW};
        Clock movingClock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return current[0]; }
        };
        var calls = new int[1];
        var flow = new ClientCredentialsFlow(request -> {
            calls[0]++;
            check("client_credentials".equals(request.form().get("grant_type")), "Expiry renews original machine grant");
            check(!request.form().containsKey("refresh_token"), "No refresh assumption");
            return new TokenResponse(200, Map.of("access_token", "machine-" + calls[0], "refresh_token", "ignored-refresh", "token_type", "Bearer", "expires_in", 120));
        }, store, movingClock);
        var first = flow.credential(config);
        check(first.accessToken().equals("machine-1") && first.refreshToken() == null, "Machine grant ignores refresh token");
        check(flow.credential(config).equals(first) && calls[0] == 1, "Valid machine token reused");
        current[0] = NOW.plusSeconds(100);
        check(flow.credential(config).accessToken().equals("machine-2") && calls[0] == 2, "Token renewed before expiry with skew");
        check(store.load(codeBinding).orElseThrow().accessToken().equals("user-access"), "User grant remains isolated");
    }

    private static void sendsClientSecretPostCredentialsAsFormParameters() {
        var good = config();
        var server = new ClientCredentialsMetadata(good.binding().issuer(), good.serverMetadata().tokenEndpoint(),
                Set.of("client_credentials"), Set.of("client_secret_post"), Set.of());
        var config = new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), server,
                good.client(), ClientCredentialsAuthentication.post());
        var seen = new ArrayList<TokenRequest>();
        new ClientCredentialsFlow(request -> {
            seen.add(request);
            return new TokenResponse(200, Map.of("access_token", "machine", "token_type", "Bearer", "expires_in", 300));
        }, new MemoryStore(), clock()).authenticate(config);
        check(seen.size() == 1, "One token request");
        check("scanner".equals(seen.get(0).form().get("client_id")), "client_id sent as form parameter");
        check("client-secret".equals(seen.get(0).form().get("client_secret")), "client_secret sent as form parameter");
        check(!seen.get(0).headers().containsKey("Authorization"), "No Authorization header");
        check(ClientCredentialsAuthentication.post().method().equals("client_secret_post"), "Method name");
    }

    private static void constructsBoundSignedAssertionsThroughSignerPort() {
        var good = config();
        var inputs = new ArrayList<String>();
        ClientAssertionSigner signer = (algorithm, signingInput) -> {
            check(algorithm.equals("RS256"), "Advertised asymmetric algorithm selected");
            inputs.add(new String(signingInput, java.nio.charset.StandardCharsets.US_ASCII));
            return new byte[] {1, 2, 3};
        };
        var server = new ClientCredentialsMetadata(good.binding().issuer(), good.serverMetadata().tokenEndpoint(),
                Set.of("client_credentials"), Set.of("private_key_jwt"), Set.of("RS256"));
        var config = new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), server,
                new RegisteredClient(good.binding().issuer(), good.client().clientId(), null),
                ClientCredentialsAuthentication.privateKeyJwt("RS256", "scanner-key", signer));
        var assertions = new ArrayList<String>();
        var flow = new ClientCredentialsFlow(request -> {
            check(!request.headers().containsKey("Authorization") && !request.form().containsKey("client_secret"), "Only JWT client authentication");
            check(!request.form().containsKey("client_id"), "Subject identifies client per extension draft");
            check(request.form().get("client_assertion_type").equals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"), "JWT assertion type");
            String assertion = request.form().get("client_assertion");
            assertions.add(assertion);
            String[] parts = assertion.split("\\.");
            check(parts.length == 3 && (parts[0] + "." + parts[1]).equals(inputs.get(inputs.size() - 1)), "Exact constructed claims signed");
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), java.nio.charset.StandardCharsets.UTF_8);
            String claims = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            check(header.equals("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"scanner-key\"}"), "Bound JWT header");
            check(claims.contains("\"iss\":\"scanner\"") && claims.contains("\"sub\":\"scanner\""), "Issuer and subject identify client");
            check(claims.contains("\"aud\":\"https://issuer.example/token\""), "Audience is exact token endpoint");
            check(claims.contains("\"iat\":" + NOW.getEpochSecond()) && claims.contains("\"exp\":" + NOW.plusSeconds(60).getEpochSecond()), "Short assertion lifetime");
            check(claims.contains("\"jti\":"), "Replay identifier present");
            check(parts[2].equals("AQID"), "Signer signature encoded without private keys in core");
            return new TokenResponse(200, Map.of("access_token", "machine", "token_type", "Bearer", "expires_in", 300));
        }, new MemoryStore(), clock());
        flow.authenticate(config);
        flow.authenticate(config);
        check(!assertions.get(0).equals(assertions.get(1)), "Fresh jti for every token request");
    }

    private static void sanitizesDenialsAndAdapterFailures() {
        var store = new MemoryStore();
        var good = config();
        var original = new OAuthCredential(good.binding(), "original-secret", null, NOW, good.binding().scopes());
        store.save(good.binding(), original);
        var denied = new ClientCredentialsFlow(request -> new TokenResponse(400, Map.of("error", "invalid_client", "error_description", "server-secret")), store, clock());
        expect(OAuthFlowException.Reason.INVALID_TOKEN_RESPONSE, () -> denied.authenticate(good));
        check(store.load(good.binding()).orElseThrow().equals(original), "Denied grant preserves previous credential");
        var failedExchange = new ClientCredentialsFlow(request -> { throw new IllegalStateException("http-secret"); }, store, clock());
        expect(OAuthFlowException.Reason.EXCHANGE_FAILED, () -> failedExchange.authenticate(good));
        CredentialStore failedStore = new CredentialStore() {
            public Optional<OAuthCredential> load(CredentialBinding binding) { throw new IllegalStateException("read-secret"); }
            public void save(CredentialBinding binding, OAuthCredential value) { throw new IllegalStateException("write-secret"); }
        };
        var storage = new ClientCredentialsFlow(request -> new TokenResponse(200, Map.of("access_token", "machine", "token_type", "Bearer")), failedStore, clock());
        expect(OAuthFlowException.Reason.STORAGE_FAILED, () -> storage.authenticate(good));
        expect(OAuthFlowException.Reason.STORAGE_FAILED, () -> storage.credential(good));
        check(!good.toString().contains("secret") && !good.authentication().toString().contains("secret"), "Config diagnostics redacted");
    }

    private static void rejectsUnsupportedSigningAndEmptySignatures() {
        var good = config();
        var server = new ClientCredentialsMetadata(good.binding().issuer(), good.serverMetadata().tokenEndpoint(),
                Set.of("client_credentials"), Set.of("private_key_jwt"), Set.of("RS256", "HS256", "none"));
        for (String algorithm : List.of("HS256", "none", "ES256")) {
            var config = new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), server, good.client(),
                    ClientCredentialsAuthentication.privateKeyJwt(algorithm, null, (alg, input) -> { throw new AssertionError("Unsupported algorithm cannot sign"); }));
            var flow = new ClientCredentialsFlow(request -> { throw new AssertionError("Invalid configuration cannot exchange"); }, new MemoryStore(), clock());
            expect(OAuthFlowException.Reason.INVALID_CONFIGURATION, () -> flow.authenticate(config));
        }
        for (ClientAssertionSigner signer : List.<ClientAssertionSigner>of((alg, input) -> null, (alg, input) -> new byte[0],
                (alg, input) -> { throw new IllegalArgumentException("key-secret"); })) {
            var config = new ClientCredentialsConfig(good.binding(), good.resourceMetadata(), server, good.client(),
                    ClientCredentialsAuthentication.privateKeyJwt("RS256", null, signer));
            var flow = new ClientCredentialsFlow(request -> { throw new AssertionError("Invalid signature cannot exchange"); }, new MemoryStore(), clock());
            expect(OAuthFlowException.Reason.EXCHANGE_FAILED, () -> flow.authenticate(config));
        }
    }

    private static void rejectsRetryStateFromAnotherGrantMode() {
        var machine = config().binding();
        var user = new CredentialBinding(machine.issuer(), machine.resource(), machine.clientId(), machine.scopes());
        var state = OAuthRetryState.start(user, "tools/list");
        try {
            new OAuthChallengePolicy().decide(401, Optional.empty(), machine, state, true, false, AuthorizationApproval.APPROVED);
            throw new AssertionError("Retry state cannot change grant modes");
        } catch (OAuthPolicyException failure) {
            check(failure.reason() == OAuthPolicyException.Reason.INVALID_RETRY_STATE, "Grant mismatch rejects retry state");
        }
    }

    private static void reusesCredentialWithNarrowerGrant() {
        var base = config();
        var binding = new CredentialBinding(base.binding().issuer(), base.binding().resource(), "scanner",
                Set.of("tools:read", "tools:write"), OAuthGrantMode.CLIENT_CREDENTIALS);
        var config = new ClientCredentialsConfig(binding, base.resourceMetadata(), base.serverMetadata(), base.client(), base.authentication());
        var calls = new int[1];
        var flow = new ClientCredentialsFlow(request -> {
            calls[0]++;
            return new TokenResponse(200, Map.of("access_token", "machine", "token_type", "Bearer", "expires_in", 300, "scope", "tools:read"));
        }, new MemoryStore(), clock());
        check(flow.credential(config).grantedScopes().equals(Set.of("tools:read")), "Narrower machine grant accepted");
        flow.credential(config);
        check(calls[0] == 1, "Narrower valid grant reused without a new token request");
    }

    private static void acceptsLocalhostAuthorizationServer() {
        URI issuer = URI.create("http://localhost:9000");
        URI resource = URI.create("http://127.0.0.1:3000/mcp");
        var binding = new CredentialBinding(issuer, resource, "scanner", Set.of("tools:read"), OAuthGrantMode.CLIENT_CREDENTIALS);
        var config = new ClientCredentialsConfig(binding, new ProtectedResourceMetadata(resource, List.of(issuer), binding.scopes()),
                new ClientCredentialsMetadata(issuer, URI.create("http://localhost:9000/token"), Set.of("client_credentials"), Set.of("client_secret_basic"), Set.of()),
                new RegisteredClient(issuer, "scanner", "client-secret"), ClientCredentialsAuthentication.basic());
        var flow = new ClientCredentialsFlow(request -> new TokenResponse(200, Map.of("access_token", "local", "token_type", "Bearer")), new MemoryStore(), clock());
        check(flow.authenticate(config).accessToken().equals("local"), "Localhost machine grant accepted");
    }

    private static ClientCredentialsMetadata metadata(URI issuer, Set<String> grants, Set<String> methods) {
        return new ClientCredentialsMetadata(issuer, URI.create("https://issuer.example/token"), grants, methods, Set.of());
    }

    private static ClientCredentialsConfig config() {
        URI issuer = URI.create("https://issuer.example");
        URI resource = URI.create("https://resource.example/mcp");
        var binding = new CredentialBinding(issuer, resource, "scanner", Set.of("tools:read"), OAuthGrantMode.CLIENT_CREDENTIALS);
        return new ClientCredentialsConfig(binding, new ProtectedResourceMetadata(resource, List.of(issuer), binding.scopes()),
                metadata(issuer, Set.of("client_credentials"), Set.of("client_secret_basic")),
                new RegisteredClient(issuer, "scanner", "client-secret"), ClientCredentialsAuthentication.basic());
    }

    private static Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void expect(OAuthFlowException.Reason reason, Runnable action) {
        try { action.run(); throw new AssertionError("Expected failure " + reason); }
        catch (OAuthFlowException failure) {
            check(failure.reason() == reason, "Typed failure");
            check(!failure.toString().contains("secret") && failure.getCause() == null, "Sanitized failure");
        }
    }
    private static final class MemoryStore implements CredentialStore {
        final Map<CredentialBinding, OAuthCredential> entries = new HashMap<>();
        public Optional<OAuthCredential> load(CredentialBinding binding) { return Optional.ofNullable(entries.get(binding)); }
        public void save(CredentialBinding binding, OAuthCredential value) { entries.put(binding, value); }
    }
}
