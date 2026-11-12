package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.catalog.crossclientresponseleak.TrialOutcome.Verdict;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.checks.shared.session.SessionHeaders;
import com.mcpscanner.checks.shared.session.SessionRefusal;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.TooManyRequests;
import com.mcpscanner.core.protocol.McpRequests;

final class HandshakeRefusal {

    private HandshakeRefusal() {
    }

    static Verdict verdict(HttpRequestResponse base, HttpRequestResponse lastHandshake) {
        if (TooManyRequests.answered(lastHandshake)) {
            return Verdict.RATE_LIMITED;
        }
        return isModern(base) && refusedInitialize(lastHandshake)
                ? Verdict.MODERN_ONLY_SERVER : Verdict.NO_PROBE_SESSION;
    }

    private static boolean refusedInitialize(HttpRequestResponse lastHandshake) {
        return refusedWithoutCredentialProblem(lastHandshake)
                && JsonRpcEnvelope.of(lastHandshake.request()).method().filter(McpMethods.INITIALIZE::equals).isPresent()
                && SessionHeaders.issuedBy(lastHandshake).isEmpty();
    }

    private static boolean isModern(HttpRequestResponse base) {
        return McpRequests.MODERN_VERSION.equals(base.request().headerValue(McpHeaders.PROTOCOL_VERSION));
    }

    private static boolean refusedWithoutCredentialProblem(HttpRequestResponse lastHandshake) {
        return SessionRefusal.refuses(lastHandshake) && !SessionRefusal.requiresCredentials(lastHandshake);
    }
}
