package com.mcpscanner.checks.shared.evidence;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.http.TextMarkers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class PayloadHighlights {

    private PayloadHighlights() {
    }

    public static HttpRequestResponse ofPayload(HttpRequestResponse exchange, AuditInsertionPoint point,
                                                String payload) {
        if (exchange == null) {
            return null;
        }
        return TextMarkers.inRequest(exchange, insertedTexts(point, payload));
    }

    public static HttpRequestResponse ofProof(HttpRequestResponse exchange, Collection<String> proofs) {
        return TextMarkers.inResponse(exchange, proofs);
    }

    private static List<String> insertedTexts(AuditInsertionPoint point, String payload) {
        try {
            ByteArray value = ByteArray.byteArray(payload);
            byte[] built = point.buildHttpRequestWithPayload(value).toByteArray().getBytes();
            return point.issueHighlights(value).stream()
                    .filter(range -> within(range, built.length))
                    .map(range -> new String(Arrays.copyOfRange(built, range.startIndexInclusive(),
                            range.endIndexExclusive()), StandardCharsets.UTF_8))
                    .toList();
        } catch (RuntimeException unsupported) {
            return List.of();
        }
    }

    private static boolean within(Range range, int length) {
        return range.startIndexInclusive() >= 0 && range.startIndexInclusive() < range.endIndexExclusive()
                && range.endIndexExclusive() <= length;
    }
}
