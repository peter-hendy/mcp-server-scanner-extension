package consumer;

import com.mcpscanner.core.oauth.*;
import java.net.URI;
import java.time.*;
import java.util.*;

public final class EnterpriseManagedConsumerTest {
    private static final URI ISSUER = URI.create("https://auth.example/");
    private static final URI RESOURCE = URI.create("https://mcp.example/api");

    public static void main(String[] args) {
        var binding = new CredentialBinding(ISSUER, RESOURCE, "https://scanner.example/client.json", Set.of("tools:read"), OAuthGrantMode.ENTERPRISE_MANAGED);
        var config = new EnterpriseManagedConfig(binding, new ProtectedResourceMetadata(RESOURCE, List.of(ISSUER), binding.scopes()),
                new EnterpriseAuthorizationMetadata(ISSUER, ISSUER.resolve("token"), Set.of("urn:ietf:params:oauth:grant-profile:id-jag"), Set.of("none"), Set.of()),
                new RegisteredClient(ISSUER, binding.clientId(), null), ClientCredentialsAuthentication.publicClient(), true);
        var saved = new HashMap<CredentialBinding, OAuthCredential>();
        CredentialStore store = new CredentialStore() {
            public Optional<OAuthCredential> load(CredentialBinding key) { return Optional.ofNullable(saved.get(key)); }
            public void save(CredentialBinding key, OAuthCredential value) { saved.put(key, value); }
        };
        var calls = new ArrayList<String>();
        var flow = new EnterpriseManagedFlow(request -> {
            calls.add("idp");
            check(request.form().get("grant_type").equals("urn:ietf:params:oauth:grant-type:token-exchange"), "Token exchange grant");
            check(request.form().get("requested_token_type").equals("urn:ietf:params:oauth:token-type:id-jag"), "ID-JAG requested");
            check(request.form().get("audience").equals(ISSUER.toString()), "AS issuer audience");
            check(request.form().get("resource").equals(RESOURCE.toString()), "Exact resource");
            check(request.form().get("scope").equals("tools:read"), "Requested scope");
            return new TokenResponse(200, Map.of("issued_token_type", "urn:ietf:params:oauth:token-type:id-jag", "access_token", "signed-id-jag", "token_type", "N_A", "expires_in", 300));
        }, request -> {
            calls.add("as");
            check(request.endpoint().equals(ISSUER.resolve("token")), "Selected token endpoint");
            check(request.form().get("grant_type").equals("urn:ietf:params:oauth:grant-type:jwt-bearer"), "JWT bearer grant");
            check(request.form().get("assertion").equals("signed-id-jag"), "ID-JAG assertion");
            check(request.form().get("client_id").equals(binding.clientId()), "CIMD client identity");
            check(!request.form().containsKey("subject_token"), "SSO token stays at IdP");
            return new TokenResponse(200, Map.of("access_token", "mcp-token", "token_type", "Bearer", "expires_in", 300));
        }, store, Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
        var credential = flow.credential(config);
        check(calls.equals(List.of("idp", "as")), "Two ordered exchanges");
        check(credential.equals(saved.get(binding)) && credential.binding().equals(binding), "Stored enterprise binding");
        flow.credential(config);
        check(calls.size() == 2, "Unexpired credential reused");
        System.out.println("EnterpriseManagedConsumerTest passed");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
