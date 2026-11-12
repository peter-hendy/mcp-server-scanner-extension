package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.logging.McpEventLog;

import java.net.URI;

final class OAuthDestinationGuard {

    private final SuspiciousDestinationGate gate;
    private final McpEventLog eventLog;

    OAuthDestinationGuard(SuspiciousDestinationGate gate, McpEventLog eventLog) {
        this.gate = gate;
        this.eventLog = eventLog;
    }

    void requireAllowed(URI destination, String purposeLabel, URI mcpResource) {
        SuspiciousDestinationGate.Decision decision = gate.evaluate(destination, FetchPurpose.of(purposeLabel, mcpResource));
        if (decision.isDenied()) {
            throw new OAuthException("Refusing to fetch " + purposeLabel + ": " + decision.userMessage());
        }
    }

    void requireAllowedIfPresent(URI destination, String purposeLabel, URI mcpResource) {
        if (destination != null) {
            requireAllowed(destination, purposeLabel, mcpResource);
        }
    }

    boolean allows(URI destination, FetchPurpose purpose) {
        boolean allowed = gate.evaluate(destination, purpose).isAllowed();
        if (!allowed) {
            eventLog.warn(purpose.label() + " destination blocked by gate: " + destination);
        }
        return allowed;
    }
}
