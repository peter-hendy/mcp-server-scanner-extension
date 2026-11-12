package com.mcpscanner.engine;

import burp.api.montoya.http.Http;

import java.util.function.Supplier;

public interface InstrumentedScan {

    Http http();

    <T> T run(String testedRequest, Supplier<T> invocation);
}
