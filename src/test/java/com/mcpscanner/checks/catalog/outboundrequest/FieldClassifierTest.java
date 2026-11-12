package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import burp.api.montoya.core.ByteArray;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.checks.catalog.outboundrequest.OutboundFixtures.field;
import static com.mcpscanner.checks.catalog.outboundrequest.OutboundFixtures.mcpRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldClassifierTest {

    private final FieldClassifier classifier = new FieldClassifier(BurpNativeJsonRpcFields.ABSENT);
    private final FieldClassifier besideBurpNativeFields = new FieldClassifier(BurpNativeJsonRpcFields.PRESENT);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void namesAToolArgumentByFieldPathMethodAndTool() {
        HttpRequest request = mcpRequest(OutboundFixtures.TOOL_CALL);

        ProbedField probed = classify(request, "MCP field: params.arguments.url").orElseThrow();

        assertThat(probed.category()).isEqualTo(FieldCategory.REQUEST_FIELD);
        assertThat(probed.location()).isEqualTo("MCP field: params.arguments.url in tools/call render_report");
    }

    @Test
    void treatsNumbersMetaFieldsResourceUrisAndHeadersAsRequestFields() {
        HttpRequest toolCall = mcpRequest(OutboundFixtures.TOOL_CALL);
        HttpRequest read = mcpRequest(OutboundFixtures.RESOURCE_READ);

        assertThat(classify(toolCall, "MCP field: params.arguments.limit")).map(ProbedField::category)
                .contains(FieldCategory.REQUEST_FIELD);
        assertThat(classify(toolCall, "MCP field: params._meta.progressToken")).map(ProbedField::category)
                .contains(FieldCategory.REQUEST_FIELD);
        assertThat(classify(toolCall, "MCP header: Mcp-Session-Id")).map(ProbedField::location)
                .contains("MCP header: Mcp-Session-Id in tools/call render_report");
        assertThat(classify(read, "MCP field: params.uri")).map(ProbedField::location)
                .contains("MCP field: params.uri in resources/read file:///notes/a.md");
    }

    @Test
    void scopesAToolArgumentToItsToolAndEveryOtherFieldToTheEndpoint() {
        HttpRequest toolCall = mcpRequest(OutboundFixtures.TOOL_CALL);
        HttpRequest read = mcpRequest(OutboundFixtures.RESOURCE_READ);

        assertThat(classify(toolCall, "MCP field: params.arguments.url")).map(ProbedField::subject)
                .contains("MCP field: params.arguments.url in tools/call render_report");
        assertThat(classify(toolCall, "MCP field: params._meta.progressToken")).map(ProbedField::subject)
                .contains("MCP field: params._meta.progressToken");
        assertThat(classify(toolCall, "MCP field: params.name")).map(ProbedField::subject)
                .contains("MCP field: params.name");
        assertThat(classify(toolCall, "MCP header: Mcp-Session-Id")).map(ProbedField::subject)
                .contains("MCP header: Mcp-Session-Id");
        assertThat(classify(read, "MCP field: params.uri")).map(ProbedField::subject)
                .contains("MCP field: params.uri");
    }

    @Test
    void scopesAnOAuthParameterToItsRequestPath() {
        HttpRequest registration = OutboundFixtures.jsonRequest("http://as.example.test/register",
                OutboundFixtures.REGISTRATION);

        assertThat(classifier.classify(registration, builtIn("client_uri", AuditInsertionPointType.PARAM_JSON)))
                .map(ProbedField::subject).contains("OAuth parameter client_uri in POST /register");
    }

    @Test
    void separatesClientMetadataFromOtherInitializeParams() {
        HttpRequest initialize = mcpRequest(OutboundFixtures.INITIALIZE);
        HttpRequest metaClientInfo = mcpRequest(OutboundFixtures.META_CLIENT_INFO);

        assertThat(classify(initialize, "MCP field: params.clientInfo.websiteUrl")).map(ProbedField::category)
                .contains(FieldCategory.CLIENT_METADATA);
        assertThat(classify(metaClientInfo, "MCP field: params._meta.io.modelcontextprotocol/clientInfo.websiteUrl"))
                .map(ProbedField::category).contains(FieldCategory.CLIENT_METADATA);
        assertThat(classify(initialize, "MCP field: params.protocolVersion")).map(ProbedField::category)
                .contains(FieldCategory.REQUEST_FIELD);
    }

    @Test
    void treatsTheUrlArgumentOfAFetchToolAsFetchingByDesign() {
        HttpRequest fetch = mcpRequest(OutboundFixtures.FETCH_TOOL_CALL);
        HttpRequest scrape = mcpRequest(OutboundFixtures.SCRAPE_TOOL_CALL);

        assertThat(classify(fetch, "MCP field: params.arguments.url")).map(ProbedField::category)
                .contains(FieldCategory.FETCH_BY_DESIGN);
        assertThat(classify(scrape, "MCP field: params.arguments.pageUrl")).map(ProbedField::category)
                .contains(FieldCategory.FETCH_BY_DESIGN);
    }

    @Test
    void keepsTheUrlOfAVendorScopedFetchToolAsARequestField() {
        HttpRequest vendor = mcpRequest(OutboundFixtures.VENDOR_FETCH_TOOL_CALL);

        assertThat(classify(vendor, "MCP field: params.arguments.url")).map(ProbedField::category)
                .contains(FieldCategory.REQUEST_FIELD);
    }

    @Test
    void keepsAUrlValuedArgumentWithoutAUrlNameAsARequestField() {
        HttpRequest fetch = mcpRequest(OutboundFixtures.FETCH_TOOL_CALL.replace("\"max_length\":5000",
                "\"callback\":\"https://hooks.example.test/a\""));

        assertThat(classify(fetch, "MCP field: params.arguments.callback")).map(ProbedField::category)
                .contains(FieldCategory.REQUEST_FIELD);
    }

    @Test
    void keepsOtherFieldsOfAFetchToolAndUrlsOfOtherToolsAsRequestFields() {
        HttpRequest fetch = mcpRequest(OutboundFixtures.FETCH_TOOL_CALL);
        HttpRequest render = mcpRequest(OutboundFixtures.TOOL_CALL);

        assertThat(classify(fetch, "MCP field: params.arguments.max_length")).map(ProbedField::category)
                .contains(FieldCategory.REQUEST_FIELD);
        assertThat(classify(fetch, "MCP header: Mcp-Session-Id")).map(ProbedField::category)
                .contains(FieldCategory.REQUEST_FIELD);
        assertThat(classify(render, "MCP field: params.arguments.url")).map(ProbedField::category)
                .contains(FieldCategory.REQUEST_FIELD);
    }

    @Test
    void namesANotificationByItsMethod() {
        HttpRequest notification = mcpRequest(OutboundFixtures.NOTIFICATION);

        assertThat(classify(notification, "MCP field: params.progressToken")).map(ProbedField::location)
                .contains("MCP field: params.progressToken in notifications/progress");
    }

    @Test
    void leavesBurpsOwnInsertionPointsOnMcpRequestsToBurp() {
        HttpRequest request = mcpRequest(OutboundFixtures.TOOL_CALL);

        assertThat(classifier.classify(request, builtIn("url", AuditInsertionPointType.PARAM_JSON))).isEmpty();
    }

    @Test
    void probesOnlyTheFetchedUrlFieldsOfAClientRegistration() {
        HttpRequest registration = OutboundFixtures.jsonRequest("http://as.example.test/register",
                OutboundFixtures.REGISTRATION);

        Optional<ProbedField> clientUri = classifier.classify(registration,
                builtIn("client_uri", AuditInsertionPointType.PARAM_JSON));

        assertThat(clientUri).map(ProbedField::category).contains(FieldCategory.OAUTH_CLIENT_METADATA);
        assertThat(clientUri).map(ProbedField::location)
                .contains("OAuth parameter client_uri in POST /register");
        assertThat(classifier.classify(registration, builtIn("client_name", AuditInsertionPointType.PARAM_JSON)))
                .isEmpty();
    }

    @Test
    void namesBurpsOwnArgumentFieldsLikeTheExtensionsFieldsWhenBurpHasNativeFields() {
        HttpRequest toolCall = mcpRequest(OutboundFixtures.FETCH_TOOL_CALL);

        Optional<ProbedField> url = besideBurpNativeFields.classify(toolCall,
                burpArgument(toolCall, "url", "\"https://docs.example.test/a\""));
        Optional<ProbedField> length = besideBurpNativeFields.classify(toolCall,
                burpArgument(toolCall, "max_length", "5000"));

        assertThat(url).map(ProbedField::location).contains("MCP field: params.arguments.url in tools/call fetch");
        assertThat(url).map(ProbedField::category).contains(FieldCategory.FETCH_BY_DESIGN);
        assertThat(url).map(ProbedField::subject).isEqualTo(classify(toolCall, "MCP field: params.arguments.url")
                .map(ProbedField::subject));
        assertThat(length).map(ProbedField::location)
                .contains("MCP field: params.arguments.max_length in tools/call fetch");
    }

    @Test
    void namesBurpsResourceUriFieldLikeTheExtensionsField() {
        HttpRequest read = mcpRequest(OutboundFixtures.RESOURCE_READ);

        assertThat(besideBurpNativeFields.classify(read, burpArgument(read, "uri", "\"file:///notes/a.md\"")))
                .map(ProbedField::location).contains("MCP field: params.uri in resources/read file:///notes/a.md");
    }

    @Test
    void ignoresBurpsGenericJsonFieldsWhenBurpHasNoNativeFields() {
        HttpRequest toolCall = mcpRequest(OutboundFixtures.FETCH_TOOL_CALL);

        assertThat(classifier.classify(toolCall, burpArgument(toolCall, "url", "\"https://docs.example.test/a\"")))
                .isEmpty();
    }

    @Test
    void ignoresBurpJsonFieldsOutsideTheArgumentsBurpFuzzesNatively() {
        HttpRequest toolCall = mcpRequest(OutboundFixtures.FETCH_TOOL_CALL);

        assertThat(besideBurpNativeFields.classify(toolCall, burpArgument(toolCall, "name", "\"fetch\""))).isEmpty();
        assertThat(besideBurpNativeFields.classify(toolCall, burpArgument(toolCall, "jsonrpc", "\"2.0\"")))
                .isEmpty();
        assertThat(besideBurpNativeFields.classify(toolCall,
                builtIn("url", AuditInsertionPointType.PARAM_URL))).isEmpty();
    }

    @Test
    void probesTheClientIdAndRequestUriOfAnAuthorizationRequest() {
        HttpRequest authorize = HttpRequest.httpRequestFromUrl(
                "http://as.example.test/authorize?response_type=code&client_id=https%3A%2F%2Fc.example.test%2Fm");

        assertThat(classifier.classify(authorize, builtIn("client_id", AuditInsertionPointType.PARAM_URL)))
                .map(ProbedField::category).contains(FieldCategory.OAUTH_CLIENT_METADATA);
        assertThat(classifier.classify(authorize, builtIn("request_uri", AuditInsertionPointType.PARAM_URL)))
                .map(ProbedField::category).contains(FieldCategory.OAUTH_CLIENT_METADATA);
        assertThat(classifier.classify(authorize, builtIn("state", AuditInsertionPointType.PARAM_URL))).isEmpty();
    }

    @Test
    void ignoresRequestsThatAreNeitherMcpNorOAuth() {
        HttpRequest plain = OutboundFixtures.post("http://app.example.test/search",
                Map.of("Content-Type", "application/json"), "{\"client_uri\":\"https://example.test\"}");

        assertThat(classifier.classify(plain, builtIn("client_uri", AuditInsertionPointType.PARAM_JSON))).isEmpty();
    }

    private Optional<ProbedField> classify(HttpRequest request, String fieldName) {
        return classifier.classify(request, field(request, fieldName));
    }

    private static AuditInsertionPoint burpArgument(HttpRequest base, String key, String writtenValue) {
        AuditInsertionPoint point = builtIn(key, AuditInsertionPointType.PARAM_JSON);
        String body = base.bodyToString();
        String written = "\"" + key + "\":" + writtenValue;
        boolean quoted = writtenValue.startsWith("\"");
        when(point.buildHttpRequestWithPayload(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            String payload = call.<ByteArray>getArgument(0).toString();
            String value = quoted ? "\"" + payload + "\"" : payload;
            return base.withBody(body.replace(written, "\"" + key + "\":" + value));
        });
        return point;
    }

    private static AuditInsertionPoint builtIn(String name, AuditInsertionPointType type) {
        AuditInsertionPoint point = mock(AuditInsertionPoint.class);
        when(point.name()).thenReturn(name);
        when(point.type()).thenReturn(type);
        return point;
    }
}
