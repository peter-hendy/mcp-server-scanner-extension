package consumer;

import com.mcpscanner.core.oauth.*;
import java.net.URI;
import java.time.*;
import java.util.*;

public final class ClientCredentialsConsumerTest {
    public static void main(String[] args) {
        URI issuer = URI.create("https://issuer.example");
        URI resource = URI.create("https://resource.example/mcp");
        var binding = new CredentialBinding(issuer, resource, "client:scanner", Set.of("tools:read"), OAuthGrantMode.CLIENT_CREDENTIALS);
        var metadata = new ClientCredentialsMetadata(issuer, URI.create("https://issuer.example/token"),
                Set.of("client_credentials"), Set.of("client_secret_basic"), Set.of());
        var config = new ClientCredentialsConfig(binding, new ProtectedResourceMetadata(resource, List.of(issuer), binding.scopes()),
                metadata, new RegisteredClient(issuer, binding.clientId(), "secret value"), ClientCredentialsAuthentication.basic());
        var saved = new HashMap<CredentialBinding, OAuthCredential>();
        CredentialStore store = new CredentialStore() {
            public Optional<OAuthCredential> load(CredentialBinding key) { return Optional.ofNullable(saved.get(key)); }
            public void save(CredentialBinding key, OAuthCredential value) { saved.put(key, value); }
        };
        var flow = new ClientCredentialsFlow(request -> {
            check("client_credentials".equals(request.form().get("grant_type")), "Explicit grant");
            check(resource.toString().equals(request.form().get("resource")), "Resource bound");
            check("tools:read".equals(request.form().get("scope")), "Scope bound");
            check(request.endpoint().equals(metadata.tokenEndpoint()), "Token endpoint bound");
            check(new String(Base64.getDecoder().decode(request.headers().get("Authorization").substring(6)),
                    java.nio.charset.StandardCharsets.UTF_8).equals("client%3Ascanner:secret+value"), "RFC Basic authentication");
            check(!request.form().containsKey("client_secret"), "One authentication method");
            return new TokenResponse(200, Map.of("access_token", "access-secret", "token_type", "Bearer", "expires_in", 300));
        }, store, Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
        var credential = flow.authenticate(config);
        check(credential.refreshToken() == null && saved.get(binding).equals(credential), "Atomic stored grant without refresh assumption");
        System.out.println("ClientCredentialsConsumerTest passed");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
