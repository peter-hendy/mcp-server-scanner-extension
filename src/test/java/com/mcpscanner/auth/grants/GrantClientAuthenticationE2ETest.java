package com.mcpscanner.auth.grants;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.oauth.BurpHttpRequestSender;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.testutil.AuthenticatedMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRealHttp;
import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GrantClientAuthenticationE2ETest {
    private static final String CLIENT_ID = "scanner";
    private static final String CLIENT_SECRET = "client-secret";
    private static final String ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private AuthenticatedMcpServer server;
    private McpScannerSession session;

    @BeforeEach
    void startServer() throws IOException {
        MontoyaTestFactory.install();
        server = AuthenticatedMcpServer.start();
        server.requireIssuedAccessToken();
        session = new McpScannerSession(HttpClient.newHttpClient(), new McpEventLog(null));
    }

    @AfterEach
    void stopServer() {
        session.disconnect();
        server.close();
    }

    @Test
    void clientSecretPostSendsCredentialsInTheFormAndNoBasicHeader() {
        server.advertiseTokenEndpointAuthMethods(List.of("client_secret_post"));

        connect(ClientCredentialsAuthentication.post());

        var tokenCall = server.tokenCalls().get(0);
        assertThat(tokenCall.authorization()).isNull();
        assertThat(tokenCall.form()).containsEntry("grant_type", "client_credentials")
                .containsEntry("client_id", CLIENT_ID).containsEntry("client_secret", CLIENT_SECRET)
                .containsEntry("resource", server.endpoint());
        assertScannerRequestsCarryBearer();
    }

    @Test
    void clientSecretBasicSendsCredentialsInTheHeaderOnly() {
        connect(ClientCredentialsAuthentication.basic());

        var tokenCall = server.tokenCalls().get(0);
        String expected = "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());
        assertThat(tokenCall.authorization()).isEqualTo(expected);
        assertThat(tokenCall.form()).doesNotContainKey("client_secret");
        assertScannerRequestsCarryBearer();
    }

    @Test
    void privateKeyJwtSendsAVerifiableAssertionPerTheClientCredentialsExtension() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        server.advertiseTokenEndpointAuthMethods(List.of("private_key_jwt"));
        var provider = signerProvider(keys);
        var authentication = new ClientAuthenticationProviderRegistry(List.of(provider)).require("test-signer");

        connect(authentication);

        var form = server.tokenCalls().get(0).form();
        assertThat(server.tokenCalls().get(0).authorization()).isNull();
        assertThat(form).containsEntry("grant_type", "client_credentials")
                .containsEntry("client_assertion_type", ASSERTION_TYPE)
                .containsEntry("resource", server.endpoint())
                .doesNotContainKeys("client_id", "client_secret");
        assertAssertionIsValid(form.get("client_assertion"), keys);
        assertScannerRequestsCarryBearer();
    }

    private void assertAssertionIsValid(String jwt, KeyPair keys) throws Exception {
        String[] parts = jwt.split("\\.");
        JsonNode header = decode(parts[0]);
        JsonNode claims = decode(parts[1]);
        assertThat(header.path("alg").asText()).isEqualTo("RS256");
        assertThat(claims.path("iss").asText()).isEqualTo(CLIENT_ID);
        assertThat(claims.path("sub").asText()).isEqualTo(CLIENT_ID);
        assertThat(claims.path("aud").toString()).contains(server.origin());
        assertThat(claims.has("jti")).isTrue();
        assertThat(claims.has("exp")).isTrue();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keys.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes());
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    private static JsonNode decode(String segment) throws IOException {
        return McpObjectMapper.INSTANCE.readTree(Base64.getUrlDecoder().decode(segment));
    }

    private static ClientAuthenticationProvider signerProvider(KeyPair keys) {
        return new ClientAuthenticationProvider() {
            public String id() { return "test-signer"; }
            public ClientCredentialsAuthentication authentication() {
                return ClientCredentialsAuthentication.privateKeyJwt("RS256", "test-key", (algorithm, input) -> {
                    try {
                        Signature signature = Signature.getInstance("SHA256withRSA");
                        signature.initSign(keys.getPrivate());
                        signature.update(input);
                        return signature.sign();
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                });
            }
        };
    }

    private void connect(ClientCredentialsAuthentication authentication) {
        var settings = new OptionalGrantSettings(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS,
                URI.create(server.origin()), CLIENT_ID, CLIENT_SECRET, Set.of(), authentication, null);
        SuspiciousDestinationGate allowLoopback = (url, purpose) -> SuspiciousDestinationGate.Decision.allow();
        AuthStrategy strategy = new OptionalGrantAuthFactory(new BurpHttpRequestSender(new RecordingRealHttp()),
                allowLoopback, new EnterpriseIdentityProviderRegistry(Map.of()), Clock.systemUTC())
                .create(URI.create(server.endpoint()), settings);
        session.connect(new McpServerConfig(server.endpoint(), TransportType.MODERN_HTTP, strategy));
    }

    private void assertScannerRequestsCarryBearer() {
        assertThat(session.catalog("tools/list", null).result().toString()).contains(AuthenticatedMcpServer.TOOL_NAME);
        assertThat(server.mcpCalls()).filteredOn(call -> call.status() != 401).isNotEmpty()
                .allSatisfy(call -> assertThat(call.header("Authorization")).isEqualTo("Bearer access-1"));
    }
}
