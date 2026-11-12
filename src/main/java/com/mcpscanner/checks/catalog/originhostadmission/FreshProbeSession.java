package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.session.SessionCleanup;
import com.mcpscanner.checks.shared.session.SessionHeaders;
import com.mcpscanner.core.connection.HttpProbeSession;

import java.util.Optional;
import java.util.function.Function;

final class FreshProbeSession {

    private final ProbeSessions sessions;
    private final CheckLog log;

    FreshProbeSession(ProbeSessions sessions, CheckLog log) {
        this.sessions = sessions;
        this.log = log;
    }

    <T> Optional<T> within(HttpRequestResponse base, HttpRequest delivered, Http http,
                           Function<HttpRequest, T> trial) {
        return open(base, http).flatMap(session -> runAndDelete(session, delivered, trial));
    }

    private <T> Optional<T> runAndDelete(HttpProbeSession session, HttpRequest delivered,
                                         Function<HttpRequest, T> trial) {
        try {
            Optional<String> sessionId = session.sessionId();
            if (sessionId.isEmpty()) {
                log.info("fresh probe session issued no session id; the captured session cannot be replaced");
            }
            return sessionId.map(id -> trial.apply(SessionHeaders.inSession(delivered, id)));
        } finally {
            SessionCleanup.delete(session);
        }
    }

    private Optional<HttpProbeSession> open(HttpRequestResponse base, Http http) {
        try {
            return sessions.openHttp(base, http);
        } catch (RuntimeException refused) {
            log.info("fresh probe session refused: " + refused.getClass().getSimpleName() + ": "
                    + refused.getMessage());
            return Optional.empty();
        }
    }
}
