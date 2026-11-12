package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.shared.session.ProbeEndpoint;
import com.mcpscanner.checks.shared.session.SessionRefusal;
import com.mcpscanner.core.connection.HttpProbeSession;
import com.mcpscanner.core.protocol.McpRequests;

import java.util.Optional;

final class BareSessionProbe {

    private final ProbeEndpoint endpoint;
    private final ProbeTraffic traffic;
    private final PingRequests pings;
    private final SessionEnforcement enforcement;
    private final SkipLog test;

    BareSessionProbe(ProbeEndpoint endpoint, ProbeTraffic traffic, PingRequests pings,
                     SessionEnforcement enforcement, SkipLog test) {
        this.endpoint = endpoint;
        this.traffic = traffic;
        this.pings = pings;
        this.enforcement = enforcement;
        this.test = test;
    }

    Optional<BareSessionUse> probe(HttpProbeSession session) {
        String version = session.session().version();
        Optional<HttpRequestResponse> refusedWithoutSession = send(pings.withoutSession(version))
                .filter(SessionRefusal::requiresCredentials);
        if (refusedWithoutSession.isEmpty()) {
            return skip(SkipReason.notApplicable("a ping without a session was not refused for missing credentials"));
        }
        Optional<HttpRequestResponse> anonymousInitializeRefused = anonymousInitializeRefusal();
        if (anonymousInitializeRefused.isEmpty()) {
            return Optional.empty();
        }
        Optional<HttpRequestResponse> unissuedIdRefused = enforcement.unissuedIdWithoutCredentials(session)
                .filter(SessionRefusal::refuses);
        if (unissuedIdRefused.isEmpty()) {
            return skip(SkipReason.prerequisiteMissing("a never-issued ID without credentials was not refused"));
        }
        return send(pings.withSession(version, session.session().sessionId()))
                .filter(PingReplies::answered)
                .map(bare -> new BareSessionUse(refusedWithoutSession.get(), anonymousInitializeRefused.get(),
                        unissuedIdRefused.get(), bare));
    }

    private Optional<HttpRequestResponse> anonymousInitializeRefusal() {
        int mark = traffic.mark();
        try (HttpProbeSession anonymous = endpoint.openWithoutCredentials()) {
            return skip(SkipReason.notApplicable("an anonymous initialize opened a session "
                    + anonymous.sessionId().orElse("without an ID") + ", so sessions are public"));
        } catch (RuntimeException refused) {
            Optional<HttpRequestResponse> refusal =
                    traffic.firstSince(mark).filter(SessionRefusal::requiresCredentials);
            return refusal.isPresent() ? refusal
                    : skip(SkipReason.notApplicable("an anonymous initialize was not refused for missing credentials"));
        }
    }

    private <T> Optional<T> skip(SkipReason reason) {
        test.notRun(reason, traffic);
        return Optional.empty();
    }

    private Optional<HttpRequestResponse> send(McpRequests.Request request) {
        return traffic.lastExchangeOf(() -> endpoint.sendWithoutCredentials(request));
    }
}
