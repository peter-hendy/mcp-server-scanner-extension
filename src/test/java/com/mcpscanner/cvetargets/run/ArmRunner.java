package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.environment.ReservedArm;
import com.mcpscanner.cvetargets.environment.RunningArm;
import com.mcpscanner.cvetargets.environment.TargetEnvironment;
import com.mcpscanner.cvetargets.manifest.ReadinessProbeType;
import com.mcpscanner.cvetargets.manifest.ReadinessSpec;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ArmRunner {

    private final TargetEnvironment environment;
    private final Map<ReadinessProbeType, ReadinessProbe> readinessProbes;
    private final ArmAudit audit;
    private final OutcomeJudge judge;
    private final Clock clock;
    private final ArmResourceMonitor resources;
    private final ArmSupervision supervision;
    private final ArmDeadline deadline = new ArmDeadline();

    public ArmRunner(TargetEnvironment environment, Map<ReadinessProbeType, ReadinessProbe> readinessProbes,
                     ArmAudit audit, Clock clock) {
        this(environment, readinessProbes, audit, clock, ArmResourceMonitor.NONE);
    }

    public ArmRunner(TargetEnvironment environment, Map<ReadinessProbeType, ReadinessProbe> readinessProbes,
                     ArmAudit audit, Clock clock, ArmResourceMonitor resources) {
        this(environment, readinessProbes, audit, clock, resources, ArmSupervision.NONE);
    }

    public ArmRunner(TargetEnvironment environment, Map<ReadinessProbeType, ReadinessProbe> readinessProbes,
                     ArmAudit audit, Clock clock, ArmResourceMonitor resources, ArmSupervision supervision) {
        this.supervision = supervision;
        this.environment = environment;
        this.readinessProbes = Map.copyOf(readinessProbes);
        this.audit = audit;
        this.judge = new OutcomeJudge();
        this.clock = clock;
        this.resources = resources;
    }

    public ArmResult run(ArmRun run) {
        return run(SharedArmRun.alone(run)).get(0);
    }

    public List<ArmResult> run(SharedArmRun shared) {
        ArmProgressReport report = new ArmProgressReport(label(shared.build()), clock,
                ArmProgressReport.DEFAULT_INTERVAL, supervision.progressLines());
        ArmTimeline timeline = new ArmTimeline(clock, report::phaseStarted);
        ArmResourceMonitor.WatchedArm watched = resources.watch(shared.build());
        List<ArmResult> results;
        try (ArmWakeLock.Held awake = supervision.wakeLock().hold()) {
            results = timedRun(shared, timeline, report);
        }
        Map<String, Long> phases = timeline.phases();
        PeakUsage peak = watched.stop();
        return results.stream().map(result -> result.measured(phases, peak)).toList();
    }

    private static String label(ArmRun run) {
        return run.target().manifest().id() + "/" + run.arm().name();
    }

    private List<ArmResult> timedRun(SharedArmRun shared, ArmTimeline timeline, ArmProgressReport report) {
        ArmRun build = shared.build();
        Instant started = clock.instant();
        ArmTeardown teardown = new ArmTeardown();
        ArmProgress progress = new ArmProgress(report);
        try (ReservedArm reserved = timeline.timed(ArmPhase.SLOT_WAIT,
                () -> environment.reserve(build.target(), build.arm()))) {
            try {
                return deadline.within(audit.armTimeout(build.target().manifest().armTimeout()),
                        () -> judged(shared, started, startAndAudit(build, reserved, teardown, progress, timeline)));
            } finally {
                timeline.timed(ArmPhase.TEARDOWN, teardown::close);
            }
        } catch (RuntimeException failure) {
            ArmObservation lastObserved = progress.latest().orElse(ArmObservation.NONE);
            return shared.manifests().stream().map(manifest -> failed(manifest, started, failure, lastObserved)).toList();
        }
    }

    private ArmObservation startAndAudit(ArmRun run, ReservedArm reserved, ArmTeardown teardown,
                                         ArmProgress progress, ArmTimeline timeline) {
        RunningArm running = teardown.hold(timeline.timed(ArmPhase.CONTAINERS_UP, reserved::start));
        run.identities().provision(running.endpoint(), run.target().manifest().readiness().timeout());
        if (run.auth() instanceof ArmBoundAuth armBound) {
            armBound.bind(running.endpoint(), run.target().manifest().readiness().timeout());
        }
        ArmEndpoint endpoint = new ArmEndpoint(running.endpoint(), run.auth(), run.arm().transport().clientTransport());
        timeline.timed(ArmPhase.READINESS, () -> awaitReady(run.target().manifest().readiness(), endpoint));
        run.identities().bind(running.endpoint());
        return timeline.timed(ArmPhase.AUDIT, () -> audit.audit(run, endpoint, teardown, progress));
    }

    private void awaitReady(ReadinessSpec spec, ArmEndpoint endpoint) {
        ReadinessProbe probe = readinessProbes.get(spec.probe());
        if (probe == null) {
            throw new IllegalStateException("No readiness probe registered for " + spec.probe());
        }
        probe.awaitReady(endpoint, spec.timeout());
        spec.authenticatedCall().ifPresent(call ->
                new PollingReadiness(new McpCallAttempt(call)).awaitReady(endpoint, spec.timeout()));
    }

    private List<ArmResult> judged(SharedArmRun shared, Instant started, ArmObservation observation) {
        return shared.manifests().stream().map(manifest -> judged(manifest, started, observation)).toList();
    }

    private ArmResult judged(ArmRun run, Instant started, ArmObservation audited) {
        ArmObservation observation = audited.of(run.checkId());
        Verdict verdict = judge.judge(run.expected(), observation.issues(), audited.ambiguousFor(run.checkId()),
                observation.probes());
        return new ArmResult(run.checkId(), run.target().manifest().id(), run.arm().name(), audit.mode(),
                run.arm().role(), run.arm().version(), run.expected(), judge.observed(observation.issues()),
                verdict.passed(), verdict.explanation(), observation.issues(), observation.baseRequestCount(),
                observation.unansweredBaseRequestCount(), observation.probes(), elapsedMillis(started),
                started.toString(), null, observation.auditDetails(), run.identities().evidence().orElse(null),
                run.checkLogLines(), null, null, audited.issuesOtherThan(run.checkId()), null);
    }

    private ArmResult failed(ArmRun run, Instant started, RuntimeException failure, ArmObservation lastAudited) {
        boolean timedOut = failure instanceof ArmTimedOut;
        String error = timedOut ? failure.getMessage() : failure.getClass().getSimpleName() + ": " + failure.getMessage();
        String explanation = timedOut ? error : "run failed: " + error;
        ArmObservation lastObserved = lastAudited.of(run.checkId());
        return new ArmResult(run.checkId(), run.target().manifest().id(), run.arm().name(), audit.mode(),
                run.arm().role(), run.arm().version(), run.expected(), null, false, explanation, lastObserved.issues(),
                lastObserved.baseRequestCount(), lastObserved.unansweredBaseRequestCount(), lastObserved.probes(),
                elapsedMillis(started), started.toString(), error, lastObserved.auditDetails(),
                run.identities().evidence().orElse(null), run.checkLogLines(), null, null,
                lastAudited.issuesOtherThan(run.checkId()), null);
    }

    private long elapsedMillis(Instant started) {
        return Duration.between(started, clock.instant()).toMillis();
    }
}
