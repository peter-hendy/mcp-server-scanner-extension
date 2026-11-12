package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.session.ProbeEndpoint;
import com.mcpscanner.checks.shared.session.UnissuedSessionId;
import com.mcpscanner.core.connection.HttpProbeSession;
import com.mcpscanner.core.protocol.McpRequests;

import java.util.List;
import java.util.Optional;
import java.util.Set;

final class SessionEnforcement {

    private final ProbeEndpoint endpoint;
    private final ProbeTraffic traffic;
    private final PingRequests pings;
    private final List<String> issuedIds;
    private final String liveSessionId;

    SessionEnforcement(ProbeEndpoint endpoint, ProbeTraffic traffic, PingRequests pings, List<String> issuedIds,
                       String liveSessionId) {
        this.endpoint = endpoint;
        this.traffic = traffic;
        this.pings = pings;
        this.issuedIds = List.copyOf(issuedIds);
        this.liveSessionId = liveSessionId;
    }

    Optional<HttpRequestResponse> unissuedId(HttpProbeSession session) {
        McpRequests.Request control = controlFor(session);
        return traffic.lastExchangeOf(() -> endpoint.send(control));
    }

    Optional<HttpRequestResponse> unissuedIdWithoutCredentials(HttpProbeSession session) {
        McpRequests.Request control = controlFor(session);
        return traffic.lastExchangeOf(() -> endpoint.sendWithoutCredentials(control));
    }

    String unissuedId() {
        return UnissuedSessionId.resembling(issuedIds, Set.of(liveSessionId));
    }

    private McpRequests.Request controlFor(HttpProbeSession session) {
        return pings.withSession(session.session().version(), unissuedId());
    }
}
