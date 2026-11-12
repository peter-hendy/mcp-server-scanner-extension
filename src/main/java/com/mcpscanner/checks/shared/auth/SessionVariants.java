package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.session.UnissuedSessionId;
import com.mcpscanner.mcp.McpHeaders;

import java.util.List;

public final class SessionVariants implements AuthVariantGenerator {

    private final String canary;

    public SessionVariants(String canary) {
        this.canary = canary;
    }

    @Override
    public List<AuthVariant> variantsFor(HttpRequest seed) {
        String invalidSessionId = UnissuedSessionId.from(canary);
        String controlTarget = new ProbeTargets(seed, canary).nonexistentChildTarget();
        return List.of(AuthVariant.controlled(VariantFamily.SESSION, "session.invalid-id",
                new RequestMutation(invalidSessionId,
                        request -> request.withHeader(McpHeaders.SESSION_ID, invalidSessionId)),
                new RequestMutation(invalidSessionId, request -> request.withPath(controlTarget)
                        .withHeader(McpHeaders.SESSION_ID, invalidSessionId))));
    }
}
