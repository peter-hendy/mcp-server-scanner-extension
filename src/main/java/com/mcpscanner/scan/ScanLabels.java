package com.mcpscanner.scan;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.engine.ScanLabel;
import com.mcpscanner.mcp.HttpOrigin;
import com.mcpscanner.mcp.McpRequestNames;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ScanLabels {

    private final ScanConnectionFacts connection;
    private final McpRequestNames requestNames;

    public ScanLabels(ScanConnectionFacts connection, McpRequestNames requestNames) {
        this.connection = Objects.requireNonNull(connection);
        this.requestNames = Objects.requireNonNull(requestNames);
    }

    public ScanLabel of(List<HttpRequestResponse> bases) {
        List<HttpRequest> requests = bases.stream().map(HttpRequestResponse::request).toList();
        return new ScanLabel(nameOf(requests), hostOf(requests), connection.transport(), connection.connectAs(),
                originOf(requests));
    }

    private String nameOf(List<HttpRequest> requests) {
        if (requests.size() == 1) {
            return "Scan " + requestNames.nameOf(requests.get(0));
        }
        return "Scan " + requests.size() + " requests";
    }

    private static String hostOf(List<HttpRequest> requests) {
        return firstService(requests).map(HttpService::host).orElse("");
    }

    private static String originOf(List<HttpRequest> requests) {
        return firstService(requests).map(service -> HttpOrigin.of(service).baseUrl()).orElse("");
    }

    private static Optional<HttpService> firstService(List<HttpRequest> requests) {
        return requests.stream().map(request -> Optional.ofNullable(request.httpService()))
                .flatMap(Optional::stream).findFirst();
    }
}
