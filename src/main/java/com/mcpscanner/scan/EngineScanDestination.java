package com.mcpscanner.scan;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;
import java.util.Objects;

final class EngineScanDestination implements ScanDestination {

    private final EngineScans scans;

    EngineScanDestination(EngineScans scans) {
        this.scans = Objects.requireNonNull(scans);
    }

    @Override
    public String name() {
        return "extension scan";
    }

    @Override
    public EngineRun start(List<HttpRequest> requests) {
        return new EngineRun(scans.start(requests.stream()
                .map(request -> HttpRequestResponse.httpRequestResponse(request, null))
                .toList()));
    }
}
