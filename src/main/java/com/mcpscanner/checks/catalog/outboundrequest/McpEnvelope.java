package com.mcpscanner.checks.catalog.outboundrequest;

import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;

import java.util.Optional;

record McpEnvelope(String method, Optional<String> target) {

    static Optional<McpEnvelope> of(byte[] body) {
        JsonRpcEnvelope envelope = JsonRpcEnvelope.of(body);
        return envelope.method()
                .filter(method -> envelope.declaresJsonRpcVersion())
                .map(method -> new McpEnvelope(method, envelope.paramsName().or(envelope::paramsUri)));
    }

    String describe() {
        return target.map(name -> method + " " + name).orElse(method);
    }
}
