package com.mcpscanner.engine;

import burp.api.montoya.http.Http;

import java.util.function.Supplier;

record CountedScan(Http http, InstrumentedScan instrumented) implements InstrumentedScan {

    @Override
    public <T> T run(String testedRequest, Supplier<T> invocation) {
        return instrumented.run(testedRequest, invocation);
    }
}
