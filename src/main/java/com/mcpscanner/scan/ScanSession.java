package com.mcpscanner.scan;

import java.util.Map;
import java.util.function.Supplier;

public interface ScanSession {

    String resolvedEndpoint();

    Map<String, String> scannerHeaders();

    static ScanSession of(Supplier<String> resolvedEndpoint, Supplier<Map<String, String>> scannerHeaders) {
        return new ScanSession() {
            @Override
            public String resolvedEndpoint() {
                return resolvedEndpoint.get();
            }

            @Override
            public Map<String, String> scannerHeaders() {
                return scannerHeaders.get();
            }
        };
    }
}
