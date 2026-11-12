package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.CredentialStripper;
import com.mcpscanner.checks.shared.auth.RequestMutation;
import com.mcpscanner.checks.shared.auth.VariantFamily;
import com.mcpscanner.checks.shared.session.SessionHeaders;
import com.mcpscanner.mcp.McpHeaders;

import java.util.Optional;

final class RetainedSessionVariant {

    static final String NAME = "session.retained-id";

    private final CredentialStripper stripper;

    RetainedSessionVariant(CredentialStripper stripper) {
        this.stripper = stripper;
    }

    Optional<AuthVariant> variantFor(HttpRequest seed) {
        return SessionHeaders.carriedBy(seed).map(sessionId -> AuthVariant.uncontrolled(VariantFamily.SESSION, NAME,
                new RequestMutation(sessionId,
                        request -> stripper.applyKeepingLiveSession(request)
                                .withHeader(McpHeaders.SESSION_ID, sessionId))));
    }

    static boolean isRetainedSession(AuthVariant variant) {
        return NAME.equals(variant.name());
    }
}
