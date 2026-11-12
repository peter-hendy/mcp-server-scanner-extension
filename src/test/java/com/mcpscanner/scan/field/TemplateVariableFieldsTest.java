package com.mcpscanner.scan.field;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.scan.UriTemplateExpansion;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RawHttpRequestStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateVariableFieldsTest {

    private final TemplateExpansionRegistry registry = new TemplateExpansionRegistry();
    private final McpFieldGenerator generator = new McpFieldGenerator(() -> false, registry, BurpNativeJsonRpcFields.ABSENT);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void expandedTemplateReadGetsOnePointPerVariableBesidesTheWholeUri() {
        registry.record(UriTemplateExpansion.expand("file:///{dir}/files/{name}.txt"));

        List<AuditInsertionPoint> fields = generator.fieldsOf(read("file:///dir/files/name.txt", Map.of()));

        assertThat(fields).extracting(AuditInsertionPoint::name).containsExactly(
                "MCP field: params.uri", "MCP template variable: dir", "MCP template variable: name",
                "MCP field: params._meta.progressToken");
        assertThat(fields.get(1).baseValue()).isEqualTo("dir");
        assertThat(fields.get(1).type()).isEqualTo(AuditInsertionPointType.PARAM_JSON);
    }

    @Test
    void besideBurpNativeFieldsTemplateVariablesStayWhileTheWholeUriIsLeftToBurp() {
        registry.record(UriTemplateExpansion.expand("file:///{dir}/files/{name}.txt"));
        McpFieldGenerator besideBurp = new McpFieldGenerator(() -> false, registry,
                com.mcpscanner.mcp.BurpNativeJsonRpcFields.PRESENT);

        assertThat(besideBurp.fieldsOf(read("file:///dir/files/name.txt", Map.of())))
                .extracting(AuditInsertionPoint::name).containsExactly(
                        "MCP template variable: dir", "MCP template variable: name",
                        "MCP field: params._meta.progressToken");
    }

    @Test
    void variablePayloadReplacesOnlyThatVariableInsideTheUri() throws Exception {
        registry.record(UriTemplateExpansion.expand("file:///{dir}/files/{name}.txt"));
        AuditInsertionPoint name = point(read("file:///dir/files/name.txt", Map.of()), "MCP template variable: name");

        HttpRequest mutated = name.buildHttpRequestWithPayload(ByteArray.byteArray("../../etc/passwd"));

        assertThat(uriOf(mutated)).isEqualTo("file:///dir/files/../../etc/passwd.txt");
    }

    @Test
    void variablePayloadIsJsonEscapedSoTheBodyStaysValid() throws Exception {
        registry.record(UriTemplateExpansion.expand("file:///{name}"));
        AuditInsertionPoint name = point(read("file:///name", Map.of()), "MCP template variable: name");

        HttpRequest mutated = name.buildHttpRequestWithPayload(ByteArray.byteArray("a\"b\\c\nd"));

        assertThat(uriOf(mutated)).isEqualTo("file:///a\"b\\c\nd");
    }

    @Test
    void variableOffsetsAreUtf8CorrectAfterMultibyteLiterals() throws Exception {
        registry.record(UriTemplateExpansion.expand("file:///日本/{name}"));
        AuditInsertionPoint name = point(read("file:///日本/name", Map.of()), "MCP template variable: name");

        assertThat(uriOf(name.buildHttpRequestWithPayload(ByteArray.byteArray("x")))).isEqualTo("file:///日本/x");
    }

    @Test
    void highlightCoversTheEncodedPayloadInsideTheUri() {
        registry.record(UriTemplateExpansion.expand("file:///{name}"));
        HttpRequest request = read("file:///name", Map.of());
        AuditInsertionPoint name = point(request, "MCP template variable: name");
        String body = new String(request.body().getBytes(), StandardCharsets.UTF_8);
        int variableStart = body.indexOf("file:///name") + "file:///".length();

        Range range = name.issueHighlights(ByteArray.byteArray("ab")).get(0);

        assertThat(range.startIndexInclusive()).isEqualTo(request.bodyOffset() + variableStart);
        assertThat(range.endIndexExclusive()).isEqualTo(request.bodyOffset() + variableStart + 2);
    }


    @Test
    void repeatedVariableNamesGetDistinctPointNames() throws Exception {
        registry.record(UriTemplateExpansion.expand("items/{id}/sub/{id}"));
        HttpRequest request = read("items/id/sub/id", Map.of());

        assertThat(generator.fieldsOf(request)).extracting(AuditInsertionPoint::name)
                .contains("MCP template variable: id", "MCP template variable: id #2");
        assertThat(uriOf(point(request, "MCP template variable: id #2")
                .buildHttpRequestWithPayload(ByteArray.byteArray("X")))).isEqualTo("items/id/sub/X");
    }


    @Test
    void unrecordedUrisAndOtherMethodsGetNoVariablePoints() {
        registry.record(UriTemplateExpansion.expand("file:///{name}"));

        assertThat(generator.fieldsOf(read("file:///other", Map.of()))).extracting(AuditInsertionPoint::name)
                .noneMatch(name -> name.startsWith("MCP template variable"));
        String subscribe = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/subscribe\","
                + "\"params\":{\"uri\":\"file:///name\"}}";
        assertThat(generator.fieldsOf(RawHttpRequestStub.post(subscribe, Map.of())))
                .extracting(AuditInsertionPoint::name).containsExactly("MCP field: params.uri");
    }

    @Test
    void uriWrittenWithJsonEscapesFallsBackToTheWholeUriField() {
        registry.record(UriTemplateExpansion.expand("file:///{name}"));
        String escaped = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"file:\\/\\/\\/name\"}}";

        assertThat(generator.fieldsOf(RawHttpRequestStub.post(escaped, Map.of())))
                .extracting(AuditInsertionPoint::name).containsExactly("MCP field: params.uri");
    }

    private AuditInsertionPoint point(HttpRequest request, String name) {
        return generator.fieldsOf(request).stream().filter(point -> point.name().equals(name)).findFirst()
                .orElseThrow();
    }

    private static HttpRequest read(String uri, Map<String, String> headers) {
        return RawHttpRequestStub.post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\",\"params\":"
                + "{\"uri\":\"" + uri + "\",\"_meta\":{\"progressToken\":\"p\"}}}", headers);
    }

    private static String uriOf(HttpRequest request) throws Exception {
        return McpObjectMapper.INSTANCE.readTree(request.body().getBytes()).path("params").path("uri").asText();
    }
}
