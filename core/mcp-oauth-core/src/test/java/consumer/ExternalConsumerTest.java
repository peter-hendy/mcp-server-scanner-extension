package consumer;

import com.mcpscanner.core.oauth.AuthorizationServerCapabilities;
import com.mcpscanner.core.oauth.AuthorizationCallback;
import com.mcpscanner.core.oauth.AuthorizationCallbackValidator;
import com.mcpscanner.core.oauth.CimdDocumentValidator;
import com.mcpscanner.core.oauth.RegistrationMode;
import com.mcpscanner.core.oauth.RegistrationRequest;
import com.mcpscanner.core.oauth.RegistrationSelector;
import java.net.URI;
import java.util.List;
import java.util.Map;

public final class ExternalConsumerTest {
    public static void main(String[] args) {
        URI location = URI.create("https://client.example/metadata.json");
        URI callback = URI.create("http://127.0.0.1:8765/callback");
        var selection = new RegistrationSelector().select(new RegistrationRequest(
                URI.create("https://issuer.example"), null, location,
                new AuthorizationServerCapabilities(true, null), callback, RegistrationMode.AUTO));
        if (selection.mode() != RegistrationMode.CIMD || !location.toString().equals(selection.clientId())) {
            throw new AssertionError("External consumer must select public CIMD client");
        }
        new CimdDocumentValidator().validate(location, Map.of("client_id", location.toString(),
                "client_name", "External client", "redirect_uris", List.of(callback.toString())), callback);
        var callbackResponse = new AuthorizationCallback("state", "https://issuer.example", "code", null, null);
        var validated = new AuthorizationCallbackValidator().validate("state", URI.create("https://issuer.example"), true, callbackResponse);
        if (!"code".equals(validated.code())) throw new AssertionError("External consumer validates callbacks");
        verifySharedTokenFlow();
        var parsed = new com.mcpscanner.core.oauth.OAuthChallengeParser().parse(List.of("Bearer error=insufficient_scope, scope=write"));
        var binding = new com.mcpscanner.core.oauth.CredentialBinding(URI.create("https://issuer.example"),
                URI.create("https://mcp.example/mcp"), "external", java.util.Set.of("read"));
        var retry = com.mcpscanner.core.oauth.OAuthRetryState.start(binding, "tools/call");
        var decision = new com.mcpscanner.core.oauth.OAuthChallengePolicy().decide(403, parsed, binding, retry,
                true, false, com.mcpscanner.core.oauth.AuthorizationApproval.APPROVED);
        if (decision.action() != com.mcpscanner.core.oauth.OAuthChallengeDecision.Action.AUTHORIZE
                || !decision.scopes().equals(java.util.Set.of("read", "write"))) throw new AssertionError("External consumer uses shared challenge policy");
        System.out.println("ExternalConsumerTest passed");
    }
    private static void verifySharedTokenFlow() {
        URI issuer = URI.create("https://issuer.example");
        var config = new com.mcpscanner.core.oauth.AuthorizationCodeConfig(issuer,
                URI.create("https://mcp.example/mcp"), issuer.resolve("/authorize"), issuer.resolve("/token"),
                new com.mcpscanner.core.oauth.RegisteredClient(issuer, "external-consumer", null),
                URI.create("http://127.0.0.1:8765/callback"), java.util.Set.of("tools:read"), true);
        var tokens = new java.util.HashMap<com.mcpscanner.core.oauth.CredentialBinding, com.mcpscanner.core.oauth.OAuthCredential>();
        var store = new com.mcpscanner.core.oauth.CredentialStore() {
            public java.util.Optional<com.mcpscanner.core.oauth.OAuthCredential> load(com.mcpscanner.core.oauth.CredentialBinding binding) {
                return java.util.Optional.ofNullable(tokens.get(binding));
            }
            public void save(com.mcpscanner.core.oauth.CredentialBinding binding, com.mcpscanner.core.oauth.OAuthCredential credential) {
                tokens.put(binding, credential);
            }
        };
        var engine = new com.mcpscanner.core.oauth.OAuthFlowEngine(
                request -> {
                    if (!request.method().equals("POST") || !request.body().contains("grant_type=")) throw new AssertionError("Core builds OAuth grant wire request");
                    return new com.mcpscanner.core.oauth.TokenResponse(200,
                            Map.of("access_token", "external-token", "refresh_token", "external-refresh", "token_type", "Bearer", "expires_in", 300));
                }, request -> new com.mcpscanner.core.oauth.BrowserCallback(config.callbackUri(),
                        new AuthorizationCallback(request.parameters().get("state"), issuer.toString(), "external-code", null, null)),
                store, java.time.Clock.systemUTC());
        if (!engine.authenticate(config).accessToken().equals("external-token")) throw new AssertionError("External consumer obtains tokens");
        if (!engine.refresh(config).refreshToken().equals("external-refresh")) throw new AssertionError("External consumer refreshes tokens");
    }
}
