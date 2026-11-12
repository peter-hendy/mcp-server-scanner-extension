package com.mcpscanner.client;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.AuthenticatedMcpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpScannerSessionStaticCredentialTest {

    record StaticCredential(String name, AuthStrategy strategy, Map<String, String> expectedHeaders) {
        @Override
        public String toString() {
            return name;
        }
    }

    private AuthenticatedMcpServer server;
    private McpScannerSession session;

    @BeforeEach
    void startServer() throws IOException {
        server = AuthenticatedMcpServer.start();
        session = sessionUsing(HttpClient.newHttpClient());
    }

    @AfterEach
    void stopServer() {
        session.disconnect();
        server.close();
    }

    static Stream<StaticCredential> staticCredentials() {
        return Stream.of(
                new StaticCredential("no auth", new NoAuthStrategy(), Map.of()),
                new StaticCredential("bearer token", new BearerTokenAuthStrategy("static-token"),
                        Map.of("Authorization", "Bearer static-token")),
                new StaticCredential("single custom header", customHeaders(Map.of("X-Api-Key", "key-1")),
                        Map.of("X-Api-Key", "key-1")),
                new StaticCredential("multiple custom headers",
                        customHeaders(orderedHeaders("X-Api-Key", "key-2", "X-Tenant", "tenant-a")),
                        Map.of("X-Api-Key", "key-2", "X-Tenant", "tenant-a")),
                new StaticCredential("custom authorization scheme",
                        customHeaders(Map.of("Authorization", "Basic dXNlcjpwYXNz")),
                        Map.of("Authorization", "Basic dXNlcjpwYXNz")));
    }

    static Stream<StaticCredential> presentedCredentials() {
        return staticCredentials().filter(credential -> !credential.expectedHeaders().isEmpty());
    }

    static Stream<Arguments> presentedCredentialsOverRemoteCleartext() {
        return presentedCredentials().flatMap(credential -> Stream.of(
                "http://remote.example/mcp",
                "HTTP://remote.example/mcp",
                "http://localhost@remote.example/mcp",
                "http://127.0.0.1.nip.io/mcp",
                "http://10.0.0.5/mcp").map(endpoint -> Arguments.of(credential, endpoint)));
    }

    @ParameterizedTest(name = "{0} over {1}")
    @MethodSource("transportsByCredential")
    void everyScannerRequestCarriesTheConfiguredCredentials(StaticCredential credential, TransportType transport) {
        server.requireHeaders(credential.expectedHeaders());

        session.connect(new McpServerConfig(server.endpoint(), transport, credential.strategy()));
        String catalog = session.catalog("tools/list", null).result().toString();

        assertThat(catalog).contains(AuthenticatedMcpServer.TOOL_NAME);
        assertThat(server.mcpCalls()).isNotEmpty().allSatisfy(call -> {
            assertThat(call.status()).isNotEqualTo(401);
            credential.expectedHeaders().forEach((name, value) -> assertThat(call.header(name)).isEqualTo(value));
        });
        assertThat(session.scannerHeaders()).containsAllEntriesOf(credential.expectedHeaders());
    }

    static Stream<Arguments> transportsByCredential() {
        return staticCredentials().flatMap(credential -> Stream.of(TransportType.MODERN_HTTP, TransportType.STREAMABLE_HTTP)
                .map(transport -> Arguments.of(credential, transport)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presentedCredentials")
    void rejectedStaticCredentialFailsConnectWithoutRefreshOrSseFallback(StaticCredential credential) {
        server.requireHeaders(Map.of("Authorization", "Bearer only-the-server-knows"));

        assertThatThrownBy(() -> session.connect(new McpServerConfig(server.endpoint(), TransportType.AUTO, credential.strategy())))
                .isInstanceOf(IllegalStateException.class);

        assertThat(credential.strategy().supportsRefresh()).isFalse();
        assertThat(server.mcpCalls()).isNotEmpty()
                .allSatisfy(call -> assertThat(call.httpMethod()).isEqualTo("POST"))
                .allSatisfy(call -> assertThat(call.status()).isEqualTo(401));
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("presentedCredentialsOverRemoteCleartext")
    void presentedCredentialsAreRefusedOverRemoteCleartextBeforeAnyRequest(StaticCredential credential, String endpoint) {
        HttpClient client = mock(HttpClient.class);

        assertThatThrownBy(() -> sessionUsing(client).connect(new McpServerConfig(endpoint, TransportType.AUTO, credential.strategy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cleartext");
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://remote.example/mcp", "http://localhost/mcp", "http://127.0.0.1/mcp", "http://[::1]/mcp"})
    void presentedCredentialsAreAllowedOverTlsOrLoopback(String endpoint) throws Exception {
        HttpClient client = unreachableClient();

        assertThatThrownBy(() -> sessionUsing(client).connect(new McpServerConfig(endpoint, TransportType.MODERN_HTTP,
                new BearerTokenAuthStrategy("static-token"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("cleartext");
        verify(client, atLeastOnce()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void credentialFreeStrategiesMayUseRemoteCleartext(boolean emptyCustomHeaders) throws Exception {
        HttpClient client = unreachableClient();
        AuthStrategy credentialFree = emptyCustomHeaders ? customHeaders(Map.of()) : new NoAuthStrategy();

        assertThatThrownBy(() -> sessionUsing(client).connect(new McpServerConfig("http://remote.example/mcp",
                TransportType.MODERN_HTTP, credentialFree)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("cleartext");
        verify(client, atLeastOnce()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static HttpClient unreachableClient() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("unreachable"));
        return client;
    }

    private static McpScannerSession sessionUsing(HttpClient client) {
        return new McpScannerSession(client, new McpEventLog(null));
    }

    private static CustomHeaderAuthStrategy customHeaders(Map<String, String> headers) {
        return new CustomHeaderAuthStrategy(headers);
    }

    private static Map<String, String> orderedHeaders(String firstName, String firstValue, String secondName, String secondValue) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(firstName, firstValue);
        headers.put(secondName, secondValue);
        return headers;
    }
}
