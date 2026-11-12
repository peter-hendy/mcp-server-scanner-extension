package com.mcpscanner.scan.field;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.mcpscanner.mcp.JsonLeaf;
import com.mcpscanner.mcp.JsonValueKind;
import com.mcpscanner.scan.UriTemplateExpansion;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class TemplateVariableInsertionPoint implements AuditInsertionPoint {

    static final String NAME_PREFIX = "MCP template variable: ";

    private final HttpRequest baseRequest;
    private final JsonLeaf uriLeaf;
    private final UriTemplateExpansion.Variable variable;
    private final String label;

    TemplateVariableInsertionPoint(HttpRequest baseRequest, JsonLeaf uriLeaf, UriTemplateExpansion.Variable variable,
                                   String label) {
        this.baseRequest = baseRequest;
        this.uriLeaf = uriLeaf;
        this.variable = variable;
        this.label = label;
    }

    @Override
    public String name() {
        return NAME_PREFIX + label;
    }

    @Override
    public String baseValue() {
        byte[] uri = uriLeaf.decodedValue().getBytes(StandardCharsets.UTF_8);
        return new String(uri, variable.startInclusive(), variable.endExclusive() - variable.startInclusive(),
                StandardCharsets.UTF_8);
    }

    @Override
    public AuditInsertionPointType type() {
        return AuditInsertionPointType.PARAM_JSON;
    }

    @Override
    public HttpRequest buildHttpRequestWithPayload(ByteArray payload) {
        int start = variableStartInBody();
        byte[] spliced = BodySplice.replace(baseRequest.body().getBytes(), start,
                start + variable.endExclusive() - variable.startInclusive(), encodedPayload(payload));
        return baseRequest.withBody(ByteArray.byteArray(spliced));
    }

    @Override
    public List<Range> issueHighlights(ByteArray payload) {
        int start = baseRequest.bodyOffset() + variableStartInBody();
        return List.of(Range.range(start, start + encodedPayload(payload).length));
    }

    private int variableStartInBody() {
        return uriLeaf.startInclusive() + 1 + variable.startInclusive();
    }

    private static byte[] encodedPayload(ByteArray payload) {
        String quoted = JsonPayloadEncoder.encode(payload.toString(), JsonValueKind.STRING);
        return quoted.substring(1, quoted.length() - 1).getBytes(StandardCharsets.UTF_8);
    }
}
