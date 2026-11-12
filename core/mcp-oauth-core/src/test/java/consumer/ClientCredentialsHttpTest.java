package consumer;

import com.mcpscanner.core.oauth.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientCredentialsHttpTest {
    public static void main(String[] args) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var key = generator.generateKeyPair();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var failure = new AtomicReference<Throwable>();
        String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        URI tokenEndpoint = URI.create(issuer + "/token");
        String response = "{\"access_token\":\"machine-access\",\"token_type\":\"Bearer\",\"expires_in\":300}";
        server.createContext("/token", request -> {
            try {
                check(request.getRequestMethod().equals("POST"), "Token grant uses POST");
                var fields = new HashMap<String, String>();
                for (String pair : new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
                    var parts = pair.split("=", 2);
                    fields.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                }
                check(fields.get("grant_type").equals("client_credentials"), "Explicit HTTP machine grant");
                check(fields.get("resource").equals("https://resource.example/mcp"), "HTTP resource binding");
                String[] assertion = fields.get("client_assertion").split("\\.");
                var verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(key.getPublic());
                verifier.update((assertion[0] + "." + assertion[1]).getBytes(StandardCharsets.US_ASCII));
                check(verifier.verify(Base64.getUrlDecoder().decode(assertion[2])), "Real private-key assertion signature verifies");
                String claims = new String(Base64.getUrlDecoder().decode(assertion[1]), StandardCharsets.UTF_8);
                check(claims.contains("\"aud\":\"" + tokenEndpoint + "\""), "Assertion audience reaches bound endpoint");
            } catch (Throwable error) { failure.set(error); }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(failure.get() == null ? 200 : 400, body.length);
            request.getResponseBody().write(body);
            request.close();
        });
        server.start();
        try {
            var binding = new CredentialBinding(URI.create(issuer), URI.create("https://resource.example/mcp"), "scanner", Set.of(), OAuthGrantMode.CLIENT_CREDENTIALS);
            var metadata = new ClientCredentialsMetadata(binding.issuer(), tokenEndpoint, Set.of("client_credentials"), Set.of("private_key_jwt"), Set.of("RS256"));
            ClientAssertionSigner signer = (algorithm, input) -> {
                try {
                    var signature = Signature.getInstance("SHA256withRSA");
                    signature.initSign(key.getPrivate());
                    signature.update(input);
                    return signature.sign();
                } catch (GeneralSecurityException error) { throw new IllegalStateException(error); }
            };
            var config = new ClientCredentialsConfig(binding, new ProtectedResourceMetadata(binding.resource(), List.of(binding.issuer()), Set.of()),
                    metadata, new RegisteredClient(binding.issuer(), "scanner", null), ClientCredentialsAuthentication.privateKeyJwt("RS256", null, signer));
            var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            TokenExchange exchange = request -> {
                try {
                    var outgoing = HttpRequest.newBuilder(request.endpoint()).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(request.body()));
                    request.headers().forEach(outgoing::header);
                    var incoming = http.send(outgoing.build(), HttpResponse.BodyHandlers.ofString());
                    check(incoming.body().equals(response), "Controlled response fixture");
                    return new TokenResponse(incoming.statusCode(), Map.of("access_token", "machine-access", "token_type", "Bearer", "expires_in", 300));
                } catch (Exception error) { throw new IllegalStateException(error); }
            };
            CredentialStore store = new CredentialStore() {
                public Optional<OAuthCredential> load(CredentialBinding key) { return Optional.empty(); }
                public void save(CredentialBinding key, OAuthCredential credential) {}
            };
            var credential = new ClientCredentialsFlow(exchange, store, Clock.systemUTC()).authenticate(config);
            if (failure.get() != null) throw new AssertionError("Token endpoint rejected request", failure.get());
            check(credential.accessToken().equals("machine-access"), "Controlled endpoint token accepted");
        } finally { server.stop(0); }
        System.out.println("ClientCredentialsHttpTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
