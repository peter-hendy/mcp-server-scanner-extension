package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.checks.shared.probe.McpMethodClass;

import java.util.Optional;

record SeedMessage(String method, McpMethodClass methodClass) {

    static Optional<SeedMessage> of(HttpRequest request) {
        if (request == null || !"POST".equalsIgnoreCase(request.method())) {
            return Optional.empty();
        }
        JsonRpcEnvelope envelope = JsonRpcEnvelope.of(request);
        return envelope.method()
                .filter(method -> envelope.isJsonRpc2())
                .map(method -> new SeedMessage(method, McpMethodClass.of(method, envelope.notification())));
    }
}
