package com.mcpscanner.scan;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.engine.EngineScan;
import com.mcpscanner.engine.McpScanEngine;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class EngineScans implements Consumer<List<HttpRequestResponse>> {

    private final McpScanEngine engine;
    private final ScanLabels labels;

    public EngineScans(McpScanEngine engine, ScanLabels labels) {
        this.engine = Objects.requireNonNull(engine);
        this.labels = Objects.requireNonNull(labels);
    }

    public EngineScan start(List<HttpRequestResponse> bases) {
        return engine.scan(bases, labels.of(bases));
    }

    @Override
    public void accept(List<HttpRequestResponse> bases) {
        start(bases);
    }
}
