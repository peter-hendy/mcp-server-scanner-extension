package com.mcpscanner.engine;

import burp.api.montoya.http.Http;

import java.util.function.BooleanSupplier;

@FunctionalInterface
public interface ScanInstrumentation {

    ScanInstrumentation NONE = (http, scanId, requestSent, cancelled) -> new UninstrumentedScan(http);

    InstrumentedScan instrument(Http http, long scanId, Runnable requestSent, BooleanSupplier cancelled);
}
