package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.checks.shared.evidence.PayloadHighlights;

import java.util.Optional;
import java.util.function.Supplier;

final class PayloadSender {

    private final Http http;
    private final AuditInsertionPoint insertionPoint;
    private final Supplier<Optional<HttpService>> probeService;

    PayloadSender(Http http, AuditInsertionPoint insertionPoint, Supplier<Optional<HttpService>> probeService) {
        this.http = http;
        this.insertionPoint = insertionPoint;
        this.probeService = probeService;
    }

    String baseValue() {
        return insertionPoint.baseValue();
    }

    HttpRequestResponse send(String value) {
        HttpRequest request = insertionPoint.buildHttpRequestWithPayload(ByteArray.byteArray(value));
        HttpRequestResponse exchange = http.sendRequest(probeService.get().map(request::withService).orElse(request));
        return PayloadHighlights.ofPayload(exchange, insertionPoint, value);
    }
}
