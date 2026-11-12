package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.session.SessionHeaders;
import com.mcpscanner.checks.shared.stream.StreamOpener;
import com.mcpscanner.core.connection.HttpProbeSession;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class SessionLifecycleAudit {

    private static final SkipReason SSE_MESSAGE_URL =
            SkipReason.notApplicable("probe sessions cannot be opened at a legacy SSE message URL");

    private final ProbeSessions probeSessions;
    private final StreamOpener streams;
    private final GuessabilityArm guessability;
    private final SseSessionLifecycleAudit sse;
    private final SessionLifecycleIssues issues;
    private final CheckLog log;

    SessionLifecycleAudit(ProbeSessions probeSessions, StreamOpener streams, GuessabilityArm guessability,
                          SseSessionLifecycleAudit sse,
                          SessionLifecycleIssues issues, CheckLog log) {
        this.probeSessions = probeSessions;
        this.guessability = guessability;
        this.sse = sse;
        this.issues = issues;
        this.streams = streams;
        this.log = log;
    }

    boolean appliesTo(HttpRequest request) {
        return SessionHeaders.carriedBy(request).isPresent() || sse.liveSessionOf(request).isPresent();
    }

    List<AuditIssue> run(HttpRequestResponse base, Http http) {
        Optional<String> sseSessionId = SessionHeaders.carriedBy(base.request()).isPresent()
                ? Optional.empty() : sse.liveSessionOf(base.request());
        if (sseSessionId.isPresent()) {
            return sse.run(base, http, sseSessionId.get());
        }
        Optional<String> liveSessionId = SessionHeaders.carriedBy(base.request());
        ProbeTraffic traffic = new ProbeTraffic();
        Optional<SessionLifecycleRun> run = liveSessionId.flatMap(live -> probeSessions.endpoint(base, http, traffic)
                .map(endpoint -> new SessionLifecycleRun(endpoint, traffic, live,
                        new DeletedStreamProbe(streams.reportingTo(http), base.request(), log), log, base.request().url())));
        if (run.isEmpty()) {
            SkipLog.ofBase(log, base).notRun(SSE_MESSAGE_URL);
            return List.of();
        }
        try (ProbeSessionPool<HttpProbeSession> pool = run.get().pool()) {
            return audit(pool, run.get(), base, liveSessionId.get());
        } finally {
            run.get().liveSessionPing().confirm(base, http);
        }
    }

    private List<AuditIssue> audit(ProbeSessionPool<HttpProbeSession> pool, SessionLifecycleRun run,
                                   HttpRequestResponse base, String liveSessionId) {
        OpenedProbes<HttpProbeSession> probes = pool.openUpTo(GuessabilityArm.PROBE_SESSIONS);
        Optional<SkipReason> skip = probes.skipReason(run.traffic());
        if (skip.isPresent()) {
            SkipLog.ofBase(log, base).notRun(skip.get());
            return List.of();
        }
        ProbeTraffic traffic = run.traffic();
        return Stream.of(
                        GuardedArm.run(run.testLog(SessionLifecycleRun.GUESSABILITY_TEST), traffic,
                                () -> guessability.judge(pool, probes, traffic, liveSessionId,
                                                sampled -> bareUse(sampled, run))
                                        .map(found -> issues.guessable(found, base))),
                        GuardedArm.run(run.testLog(SessionLifecycleRun.DELETE_TEST), traffic,
                                () -> deletedReuse(probes, run).map(reuse -> issues.deletedReuse(reuse, base))))
                .flatMap(Optional::stream).toList();
    }

    private static Optional<BareSessionUse> bareUse(OpenedProbes<HttpProbeSession> probes, SessionLifecycleRun run) {
        return probes.owned().stream().findFirst()
                .flatMap(session -> run.bareSessionProbe(probes.issuedIds()).probe(session));
    }

    private static Optional<DeletedSessionReuse> deletedReuse(OpenedProbes<HttpProbeSession> probes,
                                                              SessionLifecycleRun run) {
        Optional<HttpProbeSession> session = probes.owned().stream().findFirst();
        if (session.isEmpty()) {
            run.testLog(SessionLifecycleRun.DELETE_TEST).notRun(
                    SkipReason.prerequisiteMissing("no probe session had an ID of its own"));
        }
        return session.flatMap(owned -> run.deletedSessionProbe(probes.issuedIds()).probe(owned));
    }
}
