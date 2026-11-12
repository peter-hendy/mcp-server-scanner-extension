package com.mcpscanner.access.comparison;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.crossidentity.OperationReply;
import com.mcpscanner.checks.api.crossidentity.SessionAdmission;
import com.mcpscanner.checks.shared.session.SessionHeaders;

import java.util.Optional;

record IssuedSession(boolean admitted, Optional<String> sessionId, OperationReply refusal) {

    static IssuedSession stateless() {
        return new IssuedSession(true, Optional.empty(), OperationReply.none());
    }

    static IssuedSession joined(String sessionId) {
        return new IssuedSession(true, Optional.of(sessionId), OperationReply.none());
    }

    static IssuedSession refused(HttpRequestResponse initialize) {
        return new IssuedSession(false, Optional.empty(), SessionAdmission.refused(initialize).refusal());
    }

    boolean settled() {
        return admitted || refusal.answered();
    }

    SessionAdmission admit(HttpRequest sessionless) {
        if (!admitted) {
            return new SessionAdmission(Optional.empty(), refusal);
        }
        return SessionAdmission.admitted(sessionId.map(id -> SessionHeaders.inSession(sessionless, id))
                .orElse(sessionless));
    }
}
