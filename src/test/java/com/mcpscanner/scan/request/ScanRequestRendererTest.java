package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScanRequestRendererTest {

    private static final String MCP_ENDPOINT = "http://127.0.0.1:8000/mcp";
    private static final URI REGISTRATION_ENDPOINT = URI.create("https://auth.example.test:8443/oauth/register?tenant=a");

    private final List<String> jsonRpcRenders = new java.util.ArrayList<>();
    private final ScanRequestRenderer renderer = new ScanRequestRenderer((endpoint, message, headers) -> {
        jsonRpcRenders.add(endpoint + " " + message.method());
        return HttpRequest.httpRequestFromUrl(endpoint);
    }, new ClientRegistrationRequestFactory());
    private final ClientRegistrationMessage registration = (ClientRegistrationMessage) new ClientRegistrationRequests()
            .build(SampleSurfaces.advertisingRegistration(REGISTRATION_ENDPOINT)).get(0);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void registrationPostsTheClientMetadataAsJsonToTheRegistrationEndpoint() throws Exception {
        HttpRequest request = registration.renderWith(renderer, MCP_ENDPOINT, Map.of());

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.httpService().host()).isEqualTo("auth.example.test");
        assertThat(request.httpService().port()).isEqualTo(8443);
        assertThat(request.httpService().secure()).isTrue();
        assertThat(request.path()).isEqualTo("/oauth/register?tenant=a");
        assertThat(request.headerValue("Content-Type")).isEqualTo("application/json");
        assertThat(request.headerValue("Accept")).isEqualTo("application/json");
        assertThat(McpObjectMapper.INSTANCE.readTree(request.bodyToString())).isEqualTo(registration.metadata());
        assertThat(jsonRpcRenders).isEmpty();
    }

    @Test
    void registrationAsksBurpToCloseTheConnection() {
        HttpRequest request = registration.renderWith(renderer, MCP_ENDPOINT, Map.of());

        assertThat(request.headerValue("Connection")).isEqualTo("close");
    }

    @Test
    void registrationIsMarkedSoItsClientCanBeCleanedUpAfterTheAnswer() {
        HttpRequest request = registration.renderWith(renderer, MCP_ENDPOINT, Map.of());

        assertThat(request.hasHeader(ScannerSentinels.GENERATED_REGISTRATION_HEADER)).isTrue();
    }

    @Test
    void registrationCarriesNoMcpSessionRoutingOrCredentialHeaders() {
        Map<String, String> session = new LinkedHashMap<>();
        session.put("Mcp-Session-Id", "sess-9");
        session.put("MCP-Protocol-Version", "2026-07-28");
        session.put("X-Api-Key", "k");
        session.put("Authorization", "Bearer live");

        HttpRequest request = registration.renderWith(renderer, MCP_ENDPOINT, session);

        assertThat(request.headers()).extracting(header -> header.name().toLowerCase())
                .doesNotContain("mcp-session-id", "mcp-protocol-version", "mcp-method", "mcp-name", "x-api-key",
                        "authorization");
        assertThat(request.bodyToString()).doesNotContain("jsonrpc", "_meta");
    }

    @Test
    void jsonRpcMessagesStillRenderThroughTheMcpRendererAgainstTheMcpEndpoint() {
        ProtocolMessages.ping().renderWith(renderer, MCP_ENDPOINT, Map.of());

        assertThat(jsonRpcRenders).containsExactly(MCP_ENDPOINT + " ping");
    }
}
