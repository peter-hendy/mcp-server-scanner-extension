package com.mcpscanner.scan;

import java.util.Map;
import java.util.function.Function;

public record ScanConnection(Function<Map<String, String>, Map<String, String>> headerSupplier, Runnable validation) {

    public ScanConnection withHeaders(Function<Map<String, String>, Map<String, String>> headers) {
        return new ScanConnection(headers, validation);
    }

    public static ScanConnection unbound(Function<Map<String, String>, Map<String, String>> headerSupplier) {
        return new ScanConnection(headerSupplier, () -> {});
    }

    public Map<String, String> headers(Map<String, String> baseline) {
        requireCurrent();
        Map<String, String> headers = headerSupplier.apply(baseline);
        requireCurrent();
        return headers;
    }

    public void requireCurrent() {
        validation.run();
    }
}
