package com.mcpscanner.proxy.observe;

public final class NoopObservationSink implements McpObservationSink {

    @Override
    public void observe(ObservedMessage message) {
    }
}
