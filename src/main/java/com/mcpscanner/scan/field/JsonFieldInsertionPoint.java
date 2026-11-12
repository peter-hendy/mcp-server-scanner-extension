package com.mcpscanner.scan.field;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.mcpscanner.mcp.JsonLeaf;
import com.mcpscanner.mcp.McpFieldNames;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class JsonFieldInsertionPoint implements AuditInsertionPoint {

    static final String NAME_PREFIX = McpFieldNames.PREFIX;

    private final HttpRequest baseRequest;
    private final JsonLeaf leaf;
    private final String namePrefix;

    JsonFieldInsertionPoint(HttpRequest baseRequest, JsonLeaf leaf) {
        this(baseRequest, leaf, NAME_PREFIX);
    }

    private JsonFieldInsertionPoint(HttpRequest baseRequest, JsonLeaf leaf, String namePrefix) {
        this.baseRequest = baseRequest;
        this.leaf = leaf;
        this.namePrefix = namePrefix;
    }

    static JsonFieldInsertionPoint namedByPath(HttpRequest baseRequest, JsonLeaf leaf) {
        return new JsonFieldInsertionPoint(baseRequest, leaf, "");
    }

    @Override
    public String name() {
        return namePrefix + leaf.path();
    }

    @Override
    public String baseValue() {
        return leaf.decodedValue();
    }

    @Override
    public AuditInsertionPointType type() {
        return AuditInsertionPointType.PARAM_JSON;
    }

    @Override
    public HttpRequest buildHttpRequestWithPayload(ByteArray payload) {
        byte[] spliced = BodySplice.replace(baseRequest.body().getBytes(), leaf.startInclusive(), leaf.endExclusive(),
                encodedPayload(payload));
        return baseRequest.withBody(ByteArray.byteArray(spliced));
    }

    @Override
    public List<Range> issueHighlights(ByteArray payload) {
        int start = baseRequest.bodyOffset() + leaf.startInclusive();
        return List.of(Range.range(start, start + encodedPayload(payload).length));
    }

    private byte[] encodedPayload(ByteArray payload) {
        return JsonPayloadEncoder.encode(payload.toString(), leaf.kind()).getBytes(StandardCharsets.UTF_8);
    }
}
