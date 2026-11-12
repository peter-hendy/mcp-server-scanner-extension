package com.mcpscanner.ui.scans;

import burp.api.montoya.core.Marker;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.mcp.McpRequestNames;

import java.util.List;
import java.util.Optional;

final class TestedItem {

    static final int MAX_PARSED_BODY_BYTES = 256 * 1024;
    private static final McpRequestNames REQUEST_NAMES = new McpRequestNames();

    private TestedItem() {
    }

    static String of(AuditIssue issue, Optional<String> testedRequest) {
        return Evidence.first(issue).map(exchange -> describe(exchange, testedRequest)).orElse("");
    }

    private static String describe(HttpRequestResponse exchange, Optional<String> testedRequest) {
        HttpRequest request = exchange.request();
        if (request.body().length() > MAX_PARSED_BODY_BYTES) {
            return testedRequest.orElseGet(() -> request.method() + " " + request.path());
        }
        String item = testedRequest.orElseGet(() -> REQUEST_NAMES.nameOf(request));
        return firstMarkerStart(exchange).flatMap(offset -> PayloadField.at(request, offset))
                .map(field -> item + ScanText.SEPARATOR + field)
                .orElse(item);
    }

    private static Optional<Integer> firstMarkerStart(HttpRequestResponse exchange) {
        List<Marker> markers = exchange.requestMarkers();
        return markers == null || markers.isEmpty() ? Optional.empty()
                : Optional.of(markers.get(0).range().startIndexInclusive());
    }
}
