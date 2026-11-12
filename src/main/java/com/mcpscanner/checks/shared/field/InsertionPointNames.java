package com.mcpscanner.checks.shared.field;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.mcpscanner.mcp.BurpJsonRpcArguments;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;
import com.mcpscanner.mcp.JsonLeaf;
import com.mcpscanner.mcp.JsonLeafLocator;
import com.mcpscanner.mcp.JsonStep;
import com.mcpscanner.mcp.McpFieldNames;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class InsertionPointNames {

    private final BurpNativeJsonRpcFields burpNativeFields;

    public InsertionPointNames(BurpNativeJsonRpcFields burpNativeFields) {
        this.burpNativeFields = burpNativeFields;
    }

    public String nameOf(HttpRequest base, AuditInsertionPoint point) {
        return burpArgumentNameOf(base, point).orElseGet(point::name);
    }

    public Optional<String> burpArgumentNameOf(HttpRequest base, AuditInsertionPoint point) {
        if (point.type() != AuditInsertionPointType.PARAM_JSON) {
            return Optional.empty();
        }
        return burpNativeFields.argumentsIn(base.body().getBytes())
                .flatMap(arguments -> fuzzedSteps(point, arguments))
                .map(McpFieldNames::of);
    }

    private static Optional<List<JsonStep>> fuzzedSteps(AuditInsertionPoint point, BurpJsonRpcArguments arguments) {
        String marker = marker();
        return markedBody(point, marker).stream()
                .flatMap(body -> JsonLeafLocator.paramsLeaves(body).stream())
                .filter(leaf -> leaf.isScalar() && marker.equals(leaf.decodedValue()))
                .map(JsonLeaf::steps)
                .findFirst()
                .filter(arguments::fuzzesScalarAt);
    }

    private static String marker() {
        return "7" + ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 1_000_000_000_000_000L);
    }

    private static Optional<byte[]> markedBody(AuditInsertionPoint point, String marker) {
        try {
            return Optional.of(point.buildHttpRequestWithPayload(ByteArray.byteArray(marker)).body().getBytes());
        } catch (RuntimeException unbuildable) {
            return Optional.empty();
        }
    }
}
