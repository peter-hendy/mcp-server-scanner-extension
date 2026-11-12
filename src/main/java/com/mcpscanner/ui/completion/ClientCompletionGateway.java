package com.mcpscanner.ui.completion;

import com.mcpscanner.client.CompletionOutcome;
import com.mcpscanner.client.CompletionRequest;
import com.mcpscanner.client.McpClientManager;

import java.util.Objects;
import java.util.function.BooleanSupplier;

final class ClientCompletionGateway implements CompletionGateway {

    private final McpClientManager clientManager;
    private final BooleanSupplier connectedInUi;

    ClientCompletionGateway(McpClientManager clientManager, BooleanSupplier connectedInUi) {
        this.clientManager = Objects.requireNonNull(clientManager, "clientManager");
        this.connectedInUi = Objects.requireNonNull(connectedInUi, "connectedInUi");
    }

    @Override
    public CompletionAvailability availability() {
        if (!connectedInUi.getAsBoolean() || !clientManager.isConnected()) {
            return CompletionAvailability.DISCONNECTED;
        }
        return clientManager.completionsAdvertised()
                ? CompletionAvailability.AVAILABLE
                : CompletionAvailability.NOT_ADVERTISED;
    }

    @Override
    public CompletionOutcome complete(CompletionRequest request) {
        return clientManager.complete(request);
    }
}
