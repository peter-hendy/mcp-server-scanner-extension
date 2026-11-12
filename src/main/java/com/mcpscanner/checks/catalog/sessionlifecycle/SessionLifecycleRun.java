package com.mcpscanner.checks.catalog.sessionlifecycle;

import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.shared.session.ProbeEndpoint;
import com.mcpscanner.core.connection.HttpProbeSession;

import java.util.List;

final class SessionLifecycleRun {

    static final String DELETE_TEST = "DELETE";
    static final String GUESSABILITY_TEST = "guessability";
    private static final String BARE_SESSION_ID_TEST = "bare session ID";

    private final ProbeEndpoint endpoint;
    private final ProbeTraffic traffic;
    private final PingRequests pings = new PingRequests();
    private final String liveSessionId;
    private final DeletedStreamProbe streams;
    private final CheckLog log;
    private final String url;

    SessionLifecycleRun(ProbeEndpoint endpoint, ProbeTraffic traffic, String liveSessionId, DeletedStreamProbe streams,
                        CheckLog log, String url) {
        this.streams = streams;
        this.endpoint = endpoint;
        this.traffic = traffic;
        this.liveSessionId = liveSessionId;
        this.log = log;
        this.url = url;
    }

    ProbeSessionPool<HttpProbeSession> pool() {
        return ProbeSessionPool.closingOwnedOnly(endpoint::open, HttpProbeSession::sessionId, traffic, liveSessionId, log);
    }

    BareSessionProbe bareSessionProbe(List<String> issuedIds) {
        return new BareSessionProbe(endpoint, traffic, pings, enforcement(issuedIds), testLog(BARE_SESSION_ID_TEST));
    }

    DeletedSessionProbe deletedSessionProbe(List<String> issuedIds) {
        return new DeletedSessionProbe(new SessionPing(traffic, pings), enforcement(issuedIds), streams, traffic,
                testLog(DELETE_TEST));
    }

    LiveSessionPing liveSessionPing() {
        return new LiveSessionPing(pings, log);
    }

    ProbeTraffic traffic() {
        return traffic;
    }

    SkipLog testLog(String test) {
        return SkipLog.ofTest(log, test, url);
    }

    private SessionEnforcement enforcement(List<String> issuedIds) {
        return new SessionEnforcement(endpoint, traffic, pings, issuedIds, liveSessionId);
    }
}
