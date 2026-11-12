package com.mcpscanner.checks.api.crossidentity;

import com.mcpscanner.checks.shared.session.SessionHeaders;
import com.mcpscanner.checks.shared.session.McpSessionHandshake;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.McpHeaders;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class PrincipalSessions {

    private final OperationSeed seed;
    private final ProbeTransport transport;
    private final OpenedSessions opened;
    private static final String INITIALIZE_ID = "mcp-scanner-identity-initialize";

    private final McpSessionHandshake handshake =
            McpSessionHandshake.defaultingTo(INITIALIZE_ID, ProtocolVariant.ABSENT_HEADER_HANDSHAKE_VERSION);

    public PrincipalSessions(OperationSeed seed, ProbeTransport transport, OpenedSessions opened) {
        this.seed = seed;
        this.transport = transport;
        this.opened = opened;
    }

    public Optional<HttpRequest> fresh(HttpRequest principalRequest) {
        return admit(principalRequest).request();
    }

    public SessionAdmission admit(HttpRequest principalRequest) {
        if (!seed.stateful()) {
            return SessionAdmission.admitted(principalRequest);
        }
        HttpRequest sessionless = principalRequest.withRemovedHeader(McpHeaders.SESSION_ID);
        return admitted(sessionless, () -> sessionless);
    }

    public Optional<HttpRequest> owned(HttpRequest ownerRequest) {
        return seed.stateful() ? disposable(ownerRequest) : Optional.of(ownerRequest);
    }

    public Optional<HttpRequest> disposable(HttpRequest ownerRequest) {
        HttpRequest sessionless = ownerRequest.withRemovedHeader(McpHeaders.SESSION_ID);
        return admitted(sessionless, () -> SessionHeaders.inSession(sessionless, chosenSessionId())).request();
    }

    private SessionAdmission admitted(HttpRequest sessionless, Supplier<HttpRequest> withoutIssuedSession) {
        HttpRequestResponse admission = transport.send(handshake.initialize(sessionless));
        if (admission == null || admission.response().statusCode() / 100 != 2) {
            return SessionAdmission.refused(admission);
        }
        return SessionAdmission.admitted(SessionHeaders.issuedBy(admission)
                .map(sessionId -> joined(sessionless, sessionId))
                .orElseGet(withoutIssuedSession));
    }

    private HttpRequest joined(HttpRequest sessionless, String sessionId) {
        HttpRequest joined = SessionHeaders.inSession(sessionless, sessionId);
        opened.record(joined);
        transport.send(handshake.initialized(sessionless, sessionId));
        return joined;
    }

    private static String chosenSessionId() {
        return "mcp-scanner-" + UUID.randomUUID();
    }
}
