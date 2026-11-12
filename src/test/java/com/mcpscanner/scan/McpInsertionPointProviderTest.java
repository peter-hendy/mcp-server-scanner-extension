package com.mcpscanner.scan;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.scan.field.TemplateExpansionRegistry;
import com.mcpscanner.scan.request.ClientRegistrationMessage;
import com.mcpscanner.scan.request.RequestFamily;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ServerCapabilities;
import com.mcpscanner.scan.request.ServerSurface;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RawHttpRequestStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpInsertionPointProviderTest {

    private static final Map<String, String> REGISTRATION_HEADERS =
            Map.of("Content-Type", "application/json", "Accept", "application/json");
    private static final Map<String, String> MCP_HEADERS = Map.of("Mcp-Method", "x", "Mcp-Session-Id", "s");

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tools/call", "tools/list", "resources/read", "resources/list", "resources/templates/list",
            "resources/subscribe", "resources/unsubscribe", "prompts/get", "prompts/list", "initialize",
            "notifications/initialized", "notifications/cancelled", "notifications/progress",
            "notifications/roots/list_changed", "completion/complete", "logging/setLevel", "ping"})
    void offersParamsFieldsForEveryMcpMethod(String method) {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":{\"a\":{\"b\":[1]}}}";

        assertThat(new McpInsertionPointProvider(() -> false).provideInsertionPoints(base(body, Map.of())))
                .extracting(AuditInsertionPoint::name).containsExactly("MCP field: params.a.b[0]");
    }

    @Test
    void serverDiscoverIsRecognisedAsMcpForHeaderFields() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\"}";

        assertThat(new McpInsertionPointProvider(() -> true).provideInsertionPoints(base(body, MCP_HEADERS)))
                .extracting(AuditInsertionPoint::name)
                .containsExactlyInAnyOrder("MCP header: Mcp-Method", "MCP header: Mcp-Session-Id");
    }

    @Test
    void headerFieldsFollowTheSwitch() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":{}}";

        assertThat(new McpInsertionPointProvider(() -> false).provideInsertionPoints(base(body, MCP_HEADERS)))
                .extracting(AuditInsertionPoint::name).containsExactly("MCP field: params");
    }

    @Test
    void defaultProviderOffersHeaders() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

        assertThat(new McpInsertionPointProvider().provideInsertionPoints(base(body, MCP_HEADERS))).hasSize(2);
    }

    @Test
    void nonMcpTrafficGetsNothing() {
        McpInsertionPointProvider provider = new McpInsertionPointProvider();

        assertThat(provider.provideInsertionPoints(base("{\"method\":\"tools/call\",\"params\":{\"a\":1}}",
                MCP_HEADERS))).isEmpty();
        assertThat(provider.provideInsertionPoints(base("{\"jsonrpc\":\"2.0\",\"method\":\"custom/x\","
                + "\"params\":{\"a\":1}}", MCP_HEADERS))).isEmpty();
        assertThat(provider.provideInsertionPoints(base("hello", MCP_HEADERS))).isEmpty();
    }

    @Test
    void offersTemplateVariablesRecordedInTheSharedRegistry() {
        TemplateExpansionRegistry registry = new TemplateExpansionRegistry();
        registry.record(UriTemplateExpansion.expand("file:///{name}"));
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\",\"params\":{\"uri\":\"file:///name\"}}";

        assertThat(new McpInsertionPointProvider(() -> false, registry, BurpNativeJsonRpcFields.ABSENT).provideInsertionPoints(base(body, Map.of())))
                .extracting(AuditInsertionPoint::name)
                .containsExactly("MCP field: params.uri", "MCP template variable: name");
    }

    @Test
    void offersEveryClientMetadataFieldOfAGeneratedRegistrationUnderItsOwnName() {
        String body = registrationBody();

        assertThat(new McpInsertionPointProvider(() -> true).provideInsertionPoints(base(body, REGISTRATION_HEADERS)))
                .allMatch(point -> point.type() == AuditInsertionPointType.PARAM_JSON)
                .extracting(AuditInsertionPoint::name)
                .containsExactlyInAnyOrder("redirect_uris[0]", "token_endpoint_auth_method", "grant_types[0]",
                        "grant_types[1]", "response_types[0]", "client_name", "client_uri", "logo_uri", "scope",
                        "contacts[0]", "tos_uri", "policy_uri", "software_id", "software_version",
                        "initiate_login_uri");
    }

    @Test
    void aClientMetadataFieldCarriesThePayloadInPlaceAndKeepsTheBodyValidJson() throws Exception {
        AuditInsertionPoint logo = new McpInsertionPointProvider(() -> true)
                .provideInsertionPoints(base(registrationBody(), REGISTRATION_HEADERS)).stream()
                .filter(point -> point.name().equals("logo_uri")).findFirst().orElseThrow();

        HttpRequest probe = logo.buildHttpRequestWithPayload(ByteArray.byteArray("https://x.oastify.com/\"a"));

        JsonNode probed = McpObjectMapper.INSTANCE.readTree(probe.body().getBytes());
        assertThat(probed.path("logo_uri").asText()).isEqualTo("https://x.oastify.com/\"a");
        assertThat(probed.path("client_uri").asText()).isEqualTo(
                McpObjectMapper.INSTANCE.readTree(registrationBody()).path("client_uri").asText());
    }

    @Test
    void jsonBodiesThatAreNotClientRegistrationsStillGetNothing() {
        assertThat(new McpInsertionPointProvider().provideInsertionPoints(
                base("{\"client_uri\":\"https://c.example\"}", REGISTRATION_HEADERS))).isEmpty();
        assertThat(new McpInsertionPointProvider().provideInsertionPoints(
                base("[{\"redirect_uris\":[\"https://c.example\"]}]", REGISTRATION_HEADERS))).isEmpty();
    }

    private static String registrationBody() {
        ServerSurface surface = new ServerSurface(new ScanInventory(List.of(), List.of(), List.of(), List.of()),
                ServerCapabilities.none(), null, () -> Optional.of(URI.create("https://auth.example.test/register")));
        ClientRegistrationMessage message = (ClientRegistrationMessage) RequestGenerator.standard()
                .scanAll(surface, family -> family == RequestFamily.CLIENT_REGISTRATION).messages().get(0);
        return message.metadata().toString();
    }

    private static HttpRequestResponse base(String body, Map<String, String> headers) {
        HttpRequest request = RawHttpRequestStub.post(body, headers);
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        when(base.request()).thenReturn(request);
        return base;
    }
}
