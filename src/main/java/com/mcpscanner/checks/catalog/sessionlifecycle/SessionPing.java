package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.core.connection.HttpProbeSession;

import java.util.Optional;

final class SessionPing {

    private final ProbeTraffic traffic;
    private final PingRequests pings;

    SessionPing(ProbeTraffic traffic, PingRequests pings) {
        this.traffic = traffic;
        this.pings = pings;
    }

    Optional<HttpRequestResponse> answered(HttpProbeSession session) {
        return traffic.lastExchangeOf(() -> session.send(
                pings.withSession(session.session().version(), session.session().sessionId())))
                .filter(PingReplies::answered);
    }
}
