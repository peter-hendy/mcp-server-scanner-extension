package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.mcp.SseProbeStreams;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.session.SessionHeaders;
import com.mcpscanner.checks.shared.session.SseProbeEndpoint;
import com.mcpscanner.checks.shared.session.SseProbe;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class SseSessionLifecycleAudit {

    private static final SkipReason SSE_SESSIONS_END_WITH_THEIR_STREAM =
            SkipReason.notApplicable("a legacy SSE session ends when its stream closes");
    private static final SkipReason PROBE_STREAMS_UNAVAILABLE =
            SkipReason.prerequisiteMissing("legacy SSE probe streams are unavailable");

    private final ProbeSessions probeSessions;
    private final Supplier<Optional<SseProbeStreams>> streams;
    private final GuessabilityArm guessability;
    private final SessionLifecycleIssues issues;
    private final CheckLog log;

    SseSessionLifecycleAudit(ProbeSessions probeSessions, Supplier<Optional<SseProbeStreams>> streams,
                             GuessabilityArm guessability, SessionLifecycleIssues issues, CheckLog log) {
        this.probeSessions = probeSessions;
        this.streams = streams;
        this.guessability = guessability;
        this.issues = issues;
        this.log = log;
    }

    Optional<String> liveSessionOf(HttpRequest request) {
        return streams.get().flatMap(available -> SessionHeaders.legacySseSessionOf(request));
    }

    List<AuditIssue> run(HttpRequestResponse base, Http http, String liveSessionId) {
        ProbeTraffic traffic = new ProbeTraffic();
        Optional<SseProbeEndpoint> endpoint = streams.get()
                .map(opener -> probeSessions.sseEndpoint(base, http, traffic, opener));
        if (endpoint.isEmpty()) {
            SkipLog.ofBase(log, base).notRun(PROBE_STREAMS_UNAVAILABLE);
            return List.of();
        }
        try (ProbeSessionPool<SseProbe> pool = ProbeSessionPool.closingEverySession(endpoint.get()::open,
                SseProbe::sessionId, traffic, liveSessionId, log)) {
            return audit(pool, traffic, base, liveSessionId);
        } finally {
            new LiveSessionPing(new PingRequests(), log).confirm(base, http);
        }
    }

    private List<AuditIssue> audit(ProbeSessionPool<SseProbe> pool, ProbeTraffic traffic,
                                   HttpRequestResponse base, String liveSessionId) {
        OpenedProbes<SseProbe> probes = pool.openUpTo(GuessabilityArm.PROBE_SESSIONS);
        Optional<SkipReason> skip = probes.skipReason(traffic);
        if (skip.isPresent()) {
            SkipLog.ofBase(log, base).notRun(skip.get());
            return List.of();
        }
        String url = base.request().url();
        SkipLog.ofTest(log, SessionLifecycleRun.DELETE_TEST, url).notRun(SSE_SESSIONS_END_WITH_THEIR_STREAM);
        SkipLog guessabilityTest = SkipLog.ofTest(log, SessionLifecycleRun.GUESSABILITY_TEST, url);
        return GuardedArm.run(guessabilityTest, traffic, () -> guessability.judge(pool, probes, traffic, liveSessionId,
                sampled -> Optional.empty()).map(found -> issues.guessable(found, base))).stream().toList();
    }
}
