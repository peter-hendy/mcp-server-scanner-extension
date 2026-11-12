package com.mcpscanner.core.connection;

import com.mcpscanner.core.oauth.*;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

final class MatrixCredentials {
    private final URI issuer = URI.create("https://issuer.example");
    private final URI resource = URI.create("https://fixture.example/mcp");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);

    record Row(String name, CredentialProvider provider, Map<String, String> headers) {}

    List<Row> rows() {
        return List.of(new Row("none", CredentialProviders.none(), Map.of()),
                new Row("static-bearer", CredentialProviders.bearer("static-token"), Map.of("Authorization", "Bearer static-token")),
                new Row("custom-header", CredentialProviders.custom(Map.of("X-Api-Key", "custom-token")), Map.of("X-Api-Key", "custom-token")),
                oauth("authorization-code", authorizationCode()), oauth("machine", machine(true, true)),
                oauth("enterprise", enterprise(true)));
    }

    void rejectsMissingPrerequisites() {
        invalid(() -> machine(false, true));
        invalid(() -> machine(true, false));
        invalid(() -> enterprise(false));
    }

    private OAuthCredential authorizationCode() {
        var client = new RegisteredClient(issuer, "registered-client", null);
        var callback = URI.create("http://127.0.0.1:49152/callback");
        var config = new AuthorizationCodeConfig(issuer, resource, issuer.resolve("/authorize"), issuer.resolve("/token"),
                client, callback, Set.of("read"), true);
        var authorization = new HashMap<String, String>();
        var engine = new OAuthFlowEngine(request -> {
            equal("authorization_code", request.form().get("grant_type"));
            equal(resource.toString(), request.form().get("resource"));
            equal(client.clientId(), request.form().get("client_id"));
            equal(callback.toString(), request.form().get("redirect_uri"));
            equal("fixture-code", request.form().get("code"));
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                        request.form().get("code_verifier").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                equal(Base64.getUrlEncoder().withoutPadding().encodeToString(digest), authorization.get("code_challenge"));
            } catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
            return token("code-token");
        }, request -> {
            authorization.putAll(request.parameters());
            equal("S256", request.parameters().get("code_challenge_method"));
            return new BrowserCallback(callback, new AuthorizationCallback(request.parameters().get("state"), issuer.toString(), "fixture-code", null, null));
        }, new Store(), clock);
        return engine.authenticate(config);
    }

    private OAuthCredential machine(boolean advertised, boolean clientSecret) {
        var binding = binding("machine-client", OAuthGrantMode.CLIENT_CREDENTIALS);
        var metadata = new ClientCredentialsMetadata(issuer, issuer.resolve("/token"),
                advertised ? Set.of("client_credentials") : Set.of("authorization_code"), Set.of("client_secret_basic"), Set.of());
        var config = new ClientCredentialsConfig(binding, protectedResource(), metadata,
                new RegisteredClient(issuer, binding.clientId(), clientSecret ? "machine-secret" : null), ClientCredentialsAuthentication.basic());
        return new ClientCredentialsFlow(request -> {
            equal(true, advertised && clientSecret);
            equal("client_credentials", request.form().get("grant_type"));
            equal(resource.toString(), request.form().get("resource"));
            equal("read", request.form().get("scope"));
            equal("machine-client:machine-secret", new String(Base64.getDecoder().decode(
                    request.headers().get("Authorization").substring(6)), java.nio.charset.StandardCharsets.UTF_8));
            equal(false, request.form().containsKey("code"));
            return token("machine-token");
        }, new Store(), clock).authenticate(config);
    }

    private OAuthCredential enterprise(boolean advertised) {
        var binding = binding("https://scanner.example/client.json", OAuthGrantMode.ENTERPRISE_MANAGED);
        var config = new EnterpriseManagedConfig(binding, protectedResource(),
                new EnterpriseAuthorizationMetadata(issuer, issuer.resolve("/token"),
                        advertised ? Set.of("urn:ietf:params:oauth:grant-profile:id-jag") : Set.of(), Set.of("none"), Set.of()),
                new RegisteredClient(issuer, binding.clientId(), null), ClientCredentialsAuthentication.publicClient(), true);
        var exchanges = new ArrayList<String>();
        var credential = new EnterpriseManagedFlow(request -> {
            equal(true, advertised);
            exchanges.add("idp");
            equal("urn:ietf:params:oauth:grant-type:token-exchange", request.form().get("grant_type"));
            equal("urn:ietf:params:oauth:token-type:id-jag", request.form().get("requested_token_type"));
            equal(issuer.toString(), request.form().get("audience"));
            equal(resource.toString(), request.form().get("resource"));
            return new TokenResponse(200, Map.of("issued_token_type", "urn:ietf:params:oauth:token-type:id-jag",
                    "access_token", "identity-assertion", "token_type", "N_A", "expires_in", 300));
        }, request -> {
            equal(List.of("idp"), exchanges);
            exchanges.add("as");
            equal("urn:ietf:params:oauth:grant-type:jwt-bearer", request.form().get("grant_type"));
            equal("identity-assertion", request.form().get("assertion"));
            equal(binding.clientId(), request.form().get("client_id"));
            equal(false, request.form().containsKey("subject_token"));
            return token("enterprise-token");
        }, new Store(), clock).authenticate(config);
        equal(List.of("idp", "as"), exchanges);
        return credential;
    }

    private Row oauth(String name, OAuthCredential credential) {
        var provider = new OAuthCredentialProvider(credential.binding(), new OAuthCredentialAccess() {
            public Optional<OAuthCredential> current() { return Optional.of(credential); }
            public boolean refreshAvailable() { return false; }
            public void refresh() { throw new AssertionError("Unexpected refresh"); }
            public void authorize(Set<String> scopes) { throw new AssertionError("Unexpected authorization"); }
        }, () -> AuthorizationApproval.DENIED);
        return new Row(name, provider, Map.of("Authorization", "Bearer " + credential.accessToken()));
    }

    private CredentialBinding binding(String client, OAuthGrantMode mode) {
        return new CredentialBinding(issuer, resource, client, Set.of("read"), mode);
    }

    private ProtectedResourceMetadata protectedResource() {
        return new ProtectedResourceMetadata(resource, List.of(issuer), Set.of("read"));
    }

    private TokenResponse token(String access) {
        return new TokenResponse(200, Map.of("access_token", access, "token_type", "Bearer", "expires_in", 300, "scope", "read"));
    }

    private void invalid(Runnable operation) {
        try { operation.run(); throw new AssertionError("Missing prerequisite must reject before adapters"); }
        catch (OAuthFlowException failure) { equal(OAuthFlowException.Reason.INVALID_CONFIGURATION, failure.reason()); }
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }

    private static final class Store implements CredentialStore {
        private final Map<CredentialBinding, OAuthCredential> credentials = new HashMap<>();
        public Optional<OAuthCredential> load(CredentialBinding binding) { return Optional.ofNullable(credentials.get(binding)); }
        public void save(CredentialBinding binding, OAuthCredential credential) { credentials.put(binding, credential); }
    }
}
