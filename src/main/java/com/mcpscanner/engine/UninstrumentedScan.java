package com.mcpscanner.engine;

import burp.api.montoya.http.Http;

import java.util.function.Supplier;

record UninstrumentedScan(Http http) implements InstrumentedScan {

    @Override
    public <T> T run(String testedRequest, Supplier<T> invocation) {
        return invocation.get();
    }
}
