package com.mcpscanner.cvetargets.run;

import com.mcpscanner.ExtensionMetadata;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.core.protocol.McpRequests;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Optional;

public final class McpInitializeAttempt implements ReadinessAttempt {

    static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{"
            + "\"protocolVersion\":\"" + McpRequests.supportedInitializeVersions().get(0) + "\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"" + ExtensionMetadata.NAME + "\",\"version\":\"" + ExtensionMetadata.VERSION + "\"}}}";

    private final ReadinessRequests requests = new ReadinessRequests();

    @Override
    public Optional<String> notReadyBecause(ArmEndpoint endpoint) {
        try {
            HttpResponse<String> response = requests.post(endpoint, INITIALIZE, Optional.empty());
            boolean ready = response.statusCode() / 100 == 2 && response.body().contains("\"result\"");
            return ready ? Optional.empty() : Optional.of("status " + response.statusCode());
        } catch (IOException e) {
            return Optional.of(e.getClass().getSimpleName());
        }
    }
}
