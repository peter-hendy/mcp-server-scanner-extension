package com.mcpscanner.cvetargets.run;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.RequestCheck;
import com.mcpscanner.cvetargets.environment.RunningArm;
import com.mcpscanner.cvetargets.environment.TargetEnvironment;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.ManifestFixtures;
import com.mcpscanner.cvetargets.manifest.Outcome;
import com.mcpscanner.cvetargets.manifest.ReadinessProbeType;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.requests.BaseRequestSet;
import com.mcpscanner.cvetargets.requests.BaseRequestSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArmRunnerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path root;

    private final AtomicBoolean armClosed = new AtomicBoolean();
    private final AtomicBoolean requestsClosed = new AtomicBoolean();
    private final AtomicInteger readinessCalls = new AtomicInteger();
    private final java.util.List<java.time.Duration> readTimeouts = new java.util.ArrayList<>();
    private final CountDownLatch fixturesReleased = new CountDownLatch(1);
    private Http armHttp = answering();

    @AfterEach
    void releaseFixtures() {
        fixturesReleased.countDown();
    }

    @Test
    void runsTheCheckOnEveryBaseRequestAndJudgesTheDeclaredOutcome() {
        ArmResult result = runner(source(List.of(base(), base())), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(0), issuePerRequest(), new NoAuthStrategy()));

        assertThat(result.passed()).isTrue();
        assertThat(result.observed()).isEqualTo(Outcome.FIRE);
        assertThat(result.issues()).hasSize(2);
        assertThat(result.baseRequestCount()).isEqualTo(2);
        assertThat(result).extracting(ArmResult::check, ArmResult::target, ArmResult::arm, ArmResult::version)
                .containsExactly("probe", "target-one", "vulnerable", "1.0.0");
        assertThat(result.startedAt()).isEqualTo("2026-11-06T12:00:00Z");
        assertThat(readinessCalls).hasValue(1);
        assertThat(armClosed).isTrue();
        assertThat(requestsClosed).isTrue();
    }

    @Test
    void recordsHowLongEachLifecyclePhaseTookAndThePeakUsageOfTheArm() {
        ArmTimelineTest.SteppingClock clock = new ArmTimelineTest.SteppingClock();
        TargetEnvironment environment = (target, arm) -> {
            clock.advance(java.time.Duration.ofSeconds(7));
            return closingArm();
        };
        ArmRunner runner = new ArmRunner(environment,
                Map.of(ReadinessProbeType.MCP_INITIALIZE, (endpoint, timeout) -> clock.advance(java.time.Duration.ofSeconds(2))),
                new CheckRunnerAudit(source(List.of(base())), timeout -> armHttp, base -> List.of()),
                clock, run -> () -> new PeakUsage(512, 0, 64));

        ArmResult result = runner.run(ArmRun.asDeclared(target(), target().manifest().arms().get(0),
                issuePerRequest(), new NoAuthStrategy()));

        assertThat(result.phases()).containsKeys("slotWait", "containersUp", "readiness", "audit", "teardown");
        assertThat(result.phases()).containsEntry("containersUp", 7000L).containsEntry("readiness", 2000L);
        assertThat(result.peakUsage()).isEqualTo(new PeakUsage(512, 0, 64));
    }

    @Test
    void keepsTheMachineAwakeForTheWholeArmAndNamesEachPhaseItEnters() {
        java.util.List<String> events = new java.util.ArrayList<>();
        ArmWakeLock wakeLock = () -> {
            events.add("awake");
            return () -> events.add("may sleep");
        };
        readinessObserver = () -> events.add("ready");
        ReadinessProbe probe = (endpoint, timeout) -> readinessObserver.run();
        ArmRunner runner = new ArmRunner((target, arm) -> closingArm(), Map.of(ReadinessProbeType.MCP_INITIALIZE, probe),
                new CheckRunnerAudit(source(List.of(base())), timeout -> armHttp, base -> List.of()), CLOCK,
                ArmResourceMonitor.NONE, new ArmSupervision(wakeLock, events::add));

        runner.run(ArmRun.asDeclared(target(), target().manifest().arms().get(0), issuePerRequest(), new NoAuthStrategy()));

        assertThat(events).first().isEqualTo("awake");
        assertThat(events).last().isEqualTo("may sleep");
        assertThat(events).contains("ready",
                "cve-targets: target-one/vulnerable: containersUp (0s in)",
                "cve-targets: target-one/vulnerable: readiness (0s in)",
                "cve-targets: target-one/vulnerable: audit (0s in)",
                "cve-targets: target-one/vulnerable: teardown (0s in)");
    }

    @Test
    void bindsArmBoundAuthToTheStartedEndpointBeforeReadiness() {
        java.util.List<String> order = new java.util.ArrayList<>();
        BindingAuth auth = new BindingAuth(order);
        readinessObserver = () -> order.add("ready");

        runner(source(List.of(base())), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(0), issuePerRequest(), auth));

        assertThat(order).containsExactly("bind http://127.0.0.1:1/mcp", "ready");
    }

    @Test
    void aFailedBindFailsTheArmAndStillTearsItDown() {
        BindingAuth auth = new BindingAuth(new java.util.ArrayList<>());
        auth.failure = new IllegalStateException("login refused");

        ArmResult result = runner(source(List.of(base())), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(0), issuePerRequest(), auth));

        assertThat(result.passed()).isFalse();
        assertThat(result.error()).contains("login refused");
        assertThat(armClosed).isTrue();
    }

    private Runnable readinessObserver = () -> { };

    private static final class BindingAuth extends NoAuthStrategy implements ArmBoundAuth {
        private final java.util.List<String> order;
        private RuntimeException failure;

        BindingAuth(java.util.List<String> order) {
            this.order = order;
        }

        @Override
        public void bind(URI armEndpoint, java.time.Duration timeout) {
            if (failure != null) {
                throw failure;
            }
            order.add("bind " + armEndpoint);
        }
    }

    @Test
    void failsTheExpectedSilentArmWhenTheCheckFires() {
        ArmResult result = runner(source(List.of(base())), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(1), issuePerRequest(), new NoAuthStrategy()));

        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).startsWith("expected SILENT");
    }

    @Test
    void failsASilentArmWhoseBaseRequestsWereAllRefused() {
        ArmResult result = runner(source(List.of(baseAnswering(401), baseAnswering(400))), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(1), silentCheck(), new NoAuthStrategy()));

        assertThat(result.passed()).isFalse();
        assertThat(result.error()).contains("no base request was answered with 2xx");
        assertThat(armClosed).isTrue();
    }

    @Test
    void failsASilentArmWithNoBaseRequests() {
        ArmResult result = runner(source(List.of()), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(1), silentCheck(), new NoAuthStrategy()));

        assertThat(result.passed()).isFalse();
        assertThat(result.error()).contains("no base request was answered with 2xx");
    }

    @Test
    void passesASilentArmWhenTheCheckRanOnAnAnsweredRequest() {
        ArmResult result = runner(source(List.of(baseAnswering(401), base())), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(1), silentCheck(), new NoAuthStrategy()));

        assertThat(result.passed()).isTrue();
        assertThat(result.observed()).isEqualTo(Outcome.SILENT);
    }

    @Test
    void failsASilentArmWhenNoProbeRequestReachedTheTarget() {
        armHttp = unanswered();

        ArmResult result = runner(source(List.of(base())), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(1), silentCheck(), new NoAuthStrategy()));

        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).endsWith("but 0 of 1 probe requests reached the target");
        assertThat(result.probes()).isEqualTo(new ProbeTally(1, 0));
    }

    @Test
    void recordsTheProbeRequestsSentAndAnswered() {
        ArmResult result = runner(source(List.of(base(), base())), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(1), silentCheck(), new NoAuthStrategy()));

        assertThat(result.passed()).isTrue();
        assertThat(result.probes()).isEqualTo(new ProbeTally(2, 2));
    }

    @Test
    void passesAKnownMissThatSentNoProbe() {
        com.mcpscanner.cvetargets.manifest.ExpectedOutcome knownMiss = new com.mcpscanner.cvetargets.manifest.ExpectedOutcome(
                Outcome.SILENT, null, null, null, null, "the check skips this transport");

        ArmResult result = runner(source(List.of(base())), () -> { })
                .run(new ArmRun(target(), target().manifest().arms().get(0), unexercisedCheck(), new NoAuthStrategy(), knownMiss));

        assertThat(result.passed()).isTrue();
        assertThat(result.verdict()).startsWith("known miss (the check skips this transport)");
        assertThat(result.probes()).isEqualTo(new ProbeTally(0, 0));
    }

    @Test
    void givesTheArmHttpTheManifestReadTimeout() {
        runner(source(List.of(base())), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(0), issuePerRequest(), new NoAuthStrategy()));

        assertThat(readTimeouts).containsExactly(java.time.Duration.ofSeconds(10));
    }

    @Test
    void judgesAnotherAuditModesObservationWithTheSameJudgeAndRecordsTheMode() {
        ArmAudit burpLike = new ArmAudit() {
            @Override
            public String mode() {
                return "burp";
            }

            @Override
            public ArmObservation audit(ArmRun run, com.mcpscanner.cvetargets.requests.ArmEndpoint endpoint,
                                        ArmTeardown teardown, ArmProgress progress) {
                return new ArmObservation(List.of(new IssueSummary("Found", AuditIssueSeverity.HIGH,
                        AuditIssueConfidence.FIRM, "http://127.0.0.1:1", "detail")), 3, 0, new ProbeTally(10, 9),
                        "audit details");
            }
        };
        ArmRunner runner = new ArmRunner((target, arm) -> closingArm(),
                Map.of(ReadinessProbeType.MCP_INITIALIZE, (endpoint, timeout) -> { }), burpLike, CLOCK);

        ArmResult result = runner.run(ArmRun.asDeclared(target(), target().manifest().arms().get(0),
                issuePerRequest(), new NoAuthStrategy()));

        assertThat(result.mode()).isEqualTo("burp");
        assertThat(result.passed()).as(result.verdict()).isTrue();
        assertThat(result.baseRequestCount()).isEqualTo(3);
        assertThat(result.probes()).isEqualTo(new ProbeTally(10, 9));
        assertThat(result.auditDetails()).isEqualTo("audit details");
    }

    @Test
    void sendsProbesOverTheBaseRequestRouteAndExposesItsProbeServiceOnlyDuringTheAudit() {
        Http routed = answering();
        burp.api.montoya.http.HttpService proxy = mock(burp.api.montoya.http.HttpService.class);
        java.util.List<java.util.Optional<burp.api.montoya.http.HttpService>> seenByCheck = new java.util.ArrayList<>();
        ArmRunner runner = runner(routedSource(routed, proxy), () -> { });
        RequestCheck observing = new ProbeCheck() {
            @Override
            public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
                seenByCheck.add(audit.probeService().get());
                http.sendRequest(mock(burp.api.montoya.http.message.requests.HttpRequest.class));
                return List.of();
            }
        };

        ArmResult result = runner.run(ArmRun.asDeclared(target(), target().manifest().arms().get(1), observing, new NoAuthStrategy()));

        assertThat(result.passed()).isTrue();
        assertThat(seenByCheck).containsExactly(java.util.Optional.of(proxy));
        assertThat(audit.probeService().get()).isEmpty();
        org.mockito.Mockito.verify(routed).sendRequest(org.mockito.ArgumentMatchers.any(burp.api.montoya.http.message.requests.HttpRequest.class));
    }

    @Test
    void countsOnlyProbesTheBaseRequestRouteSaysReachedTheTarget() {
        BaseRequestSource fabricatingProxy = (endpoint, seeds, http) -> new BaseRequestSet() {
            @Override
            public List<HttpRequestResponse> requests() {
                return List.of(base());
            }

            @Override
            public int unansweredCount() {
                return 0;
            }

            @Override
            public boolean reachedTarget(HttpRequestResponse exchange) {
                return false;
            }

            @Override
            public void close() {
            }
        };

        ArmResult result = runner(fabricatingProxy, () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(1), silentCheck(), new NoAuthStrategy()));

        assertThat(result.passed()).isFalse();
        assertThat(result.probes()).isEqualTo(new ProbeTally(1, 0));
    }

    private BaseRequestSource routedSource(Http routed, burp.api.montoya.http.HttpService proxy) {
        return (endpoint, seeds, http) -> new BaseRequestSet() {
            @Override
            public List<HttpRequestResponse> requests() {
                return List.of(base());
            }

            @Override
            public int unansweredCount() {
                return 0;
            }

            @Override
            public Http route(Http armHttp) {
                return routed;
            }

            @Override
            public java.util.Optional<burp.api.montoya.http.HttpService> probeService() {
                return java.util.Optional.of(proxy);
            }

            @Override
            public void close() {
                requestsClosed.set(true);
            }
        };
    }

    private static RequestCheck unexercisedCheck() {
        return new ProbeCheck() {
            @Override
            public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
                return List.of();
            }
        };
    }

    @Test
    void tearsDownAndRecordsTheErrorWhenReadinessFails() {
        ArmResult result = runner(source(List.of(base())), () -> {
            throw new IllegalStateException("never ready");
        }).run(ArmRun.asDeclared(target(), target().manifest().arms().get(0), issuePerRequest(), new NoAuthStrategy()));

        assertThat(result.passed()).isFalse();
        assertThat(result.observed()).isNull();
        assertThat(result.error()).isEqualTo("IllegalStateException: never ready");
        assertThat(armClosed).isTrue();
    }

    @Test
    void tearsDownWhenTheCheckThrows() {
        RequestCheck throwing = new ProbeCheck() {
            @Override
            public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
                throw new IllegalArgumentException("boom");
            }
        };

        ArmResult result = runner(source(List.of(base())), () -> { })
                .run(ArmRun.asDeclared(target(), target().manifest().arms().get(0), throwing, new NoAuthStrategy()));

        assertThat(result.error()).isEqualTo("IllegalArgumentException: boom");
        assertThat(requestsClosed).isTrue();
        assertThat(armClosed).isTrue();
    }

    @Test
    void failsAHungArmAsTimedOutAndTearsItDown() {
        LoadedTarget target = targetTimingOutAfterOneSecond();
        java.util.concurrent.CountDownLatch readinessReleased = new java.util.concurrent.CountDownLatch(1);

        ArmResult result = runner(source(List.of(base())), () -> awaitUninterruptibly(readinessReleased))
                .run(ArmRun.asDeclared(target, target.manifest().arms().get(0), issuePerRequest(), new NoAuthStrategy()));
        readinessReleased.countDown();

        assertThat(result.passed()).isFalse();
        assertThat(result.observed()).isNull();
        assertThat(result.verdict()).isEqualTo("arm timed out after 1 s");
        assertThat(result.error()).isEqualTo("arm timed out after 1 s");
        assertThat(armClosed).isTrue();
    }

    @Test
    void aTimedOutArmKeepsTheLastObservationItsAuditRecorded() {
        LoadedTarget target = targetTimingOutAfterOneSecond();
        java.util.concurrent.CountDownLatch auditReleased = new java.util.concurrent.CountDownLatch(1);
        ArmAudit stillScanning = new ArmAudit() {
            @Override
            public String mode() {
                return "burp";
            }

            @Override
            public ArmObservation audit(ArmRun run, com.mcpscanner.cvetargets.requests.ArmEndpoint endpoint,
                                        ArmTeardown teardown, ArmProgress progress) {
                progress.record(new ArmObservation(List.of(new IssueSummary("Found", AuditIssueSeverity.LOW,
                        AuditIssueConfidence.FIRM, "http://127.0.0.1:1", "detail")), 113, 0, new ProbeTally(7, 7),
                        "partial audit"));
                awaitUninterruptibly(auditReleased);
                throw new IllegalStateException("torn down");
            }
        };
        ArmRunner runner = new ArmRunner((loaded, arm) -> closingArm(),
                Map.of(ReadinessProbeType.MCP_INITIALIZE, (endpoint, timeout) -> { }), stillScanning, CLOCK);

        ArmResult result = runner.run(ArmRun.asDeclared(target, target.manifest().arms().get(0), issuePerRequest(),
                new NoAuthStrategy()));
        auditReleased.countDown();

        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).isEqualTo("arm timed out after 1 s");
        assertThat(result.observed()).isNull();
        assertThat(result.probes()).isEqualTo(new ProbeTally(7, 7));
        assertThat(result.issues()).extracting(IssueSummary::name).containsExactly("Found");
        assertThat(result.baseRequestCount()).isEqualTo(113);
        assertThat(result.auditDetails()).isEqualTo("partial audit");
    }

    @Test
    void tearsDownAnArmWhoseStartOutlastsTheDeadline() throws Exception {
        LoadedTarget target = targetTimingOutAfterOneSecond();
        java.util.concurrent.CountDownLatch startReleased = new java.util.concurrent.CountDownLatch(1);
        TargetEnvironment slowToStart = (loaded, arm) -> {
            awaitUninterruptibly(startReleased);
            return closingArm();
        };

        ArmResult result = runner(slowToStart, source(List.of(base())), () -> { })
                .run(ArmRun.asDeclared(target, target.manifest().arms().get(0), issuePerRequest(), new NoAuthStrategy()));
        startReleased.countDown();

        assertThat(result.verdict()).isEqualTo("arm timed out after 1 s");
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilTrue(armClosed);
    }

    @Test
    void waitingForAMachineSlotDoesNotConsumeTheArmDeadline() {
        LoadedTarget target = targetTimingOutAfterOneSecond();

        ArmResult result = runner(slotFreedAfter(java.time.Duration.ofMillis(1500)), source(List.of(base())), () -> { })
                .run(ArmRun.asDeclared(target, target.manifest().arms().get(0), issuePerRequest(), new NoAuthStrategy()));

        assertThat(result.passed()).as(result.verdict()).isTrue();
        assertThat(armClosed).isTrue();
    }

    @Test
    void theDeadlineStillBoundsAnArmThatWaitedForItsSlot() {
        LoadedTarget target = targetTimingOutAfterOneSecond();
        java.util.concurrent.CountDownLatch readinessReleased = new java.util.concurrent.CountDownLatch(1);

        ArmResult result = runner(slotFreedAfter(java.time.Duration.ofMillis(1500)), source(List.of(base())),
                () -> awaitUninterruptibly(readinessReleased))
                .run(ArmRun.asDeclared(target, target.manifest().arms().get(0), issuePerRequest(), new NoAuthStrategy()));
        readinessReleased.countDown();

        assertThat(result.verdict()).isEqualTo("arm timed out after 1 s");
        assertThat(armClosed).isTrue();
    }

    private TargetEnvironment slotFreedAfter(java.time.Duration slotWait) {
        return new TargetEnvironment() {
            @Override
            public RunningArm start(LoadedTarget target, com.mcpscanner.cvetargets.manifest.TargetArm arm) {
                return reserve(target, arm).start();
            }

            @Override
            public com.mcpscanner.cvetargets.environment.ReservedArm reserve(
                    LoadedTarget target, com.mcpscanner.cvetargets.manifest.TargetArm arm) {
                holdFor(slotWait);
                return new com.mcpscanner.cvetargets.environment.ReservedArm(ArmRunnerTest.this::closingArm, () -> { });
            }
        };
    }

    private void holdFor(java.time.Duration wait) {
        try {
            fixturesReleased.await(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void refusesProbesFromAnArmAbandonedAtItsDeadline() {
        LoadedTarget target = targetTimingOutAfterOneSecond();
        java.util.concurrent.CountDownLatch readinessReleased = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean probeRefused = new AtomicBoolean();
        armHttp = mock(Http.class);
        RequestCheck probing = new ProbeCheck() {
            @Override
            public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
                try {
                    http.sendRequest(mock(burp.api.montoya.http.message.requests.HttpRequest.class));
                } catch (IllegalStateException refused) {
                    probeRefused.set(true);
                }
                return List.of();
            }
        };

        ArmResult result = runner(source(List.of(base())), () -> awaitUninterruptibly(readinessReleased))
                .run(ArmRun.asDeclared(target, target.manifest().arms().get(0), probing, new NoAuthStrategy()));
        readinessReleased.countDown();

        assertThat(result.verdict()).isEqualTo("arm timed out after 1 s");
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilTrue(probeRefused);
        org.mockito.Mockito.verifyNoInteractions(armHttp);
    }

    private static void awaitUninterruptibly(java.util.concurrent.CountDownLatch latch) {
        while (latch.getCount() > 0) {
            try {
                latch.await();
            } catch (InterruptedException ignored) {
                Thread.interrupted();
            }
        }
    }

    private LoadedTarget targetTimingOutAfterOneSecond() {
        ManifestFixtures.writeTarget(root, "check-a", "slow-target", ManifestFixtures.VALID_MANIFEST
                .formatted("slow-target", "check-a")
                .replace("\"auth\": { \"scheme\": \"none\" },", "\"auth\": { \"scheme\": \"none\" }, \"armTimeoutSeconds\": 1,"));
        return ManifestFixtures.loadOnly(root, "check-a", "slow-target");
    }

    private RunningArm closingArm() {
        return new RunningArm() {
            @Override
            public URI endpoint() {
                return URI.create("http://127.0.0.1:1/mcp");
            }

            @Override
            public void close() {
                armClosed.set(true);
            }
        };
    }

    @Test
    void startsASharedTargetBuildOnceAndJudgesEachManifestOnItsOwnChecksIssues() {
        AtomicInteger starts = new AtomicInteger();
        TargetEnvironment environment = (target, arm) -> {
            starts.incrementAndGet();
            return closingArm();
        };
        IssueSummary traversal = found("Traversal");
        IssueSummary outbound = found("Outbound");
        ArmRunner runner = new ArmRunner(environment, Map.of(ReadinessProbeType.MCP_INITIALIZE, (endpoint, timeout) -> { }),
                attributedAudit(new ArmObservation(List.of(traversal, outbound), 4, 0, new ProbeTally(9, 9), "details",
                        Map.of("probe", new CheckFindings(List.of(traversal), new ProbeTally(5, 5)),
                                "other", new CheckFindings(List.of(outbound), new ProbeTally(4, 4))))), CLOCK);
        ArmRun firing = ArmRun.asDeclared(target(), target().manifest().arms().get(0), issuePerRequest(),
                new NoAuthStrategy());
        ArmRun silent = ArmRun.asDeclared(otherTarget(), otherTarget().manifest().arms().get(1), checkWithId("other"),
                new NoAuthStrategy());

        List<ArmResult> results = runner.run(new SharedArmRun(firing, List.of(firing, silent)));

        assertThat(starts).hasValue(1);
        assertThat(armClosed).isTrue();
        assertThat(results).extracting(ArmResult::check, ArmResult::target, ArmResult::arm, ArmResult::passed)
                .containsExactly(tuple("probe", "target-one", "vulnerable", true),
                        tuple("other", "target-two", "fixed", false));
        assertThat(results.get(0).issues()).containsExactly(traversal);
        assertThat(results.get(0).probes()).isEqualTo(new ProbeTally(5, 5));
        assertThat(results.get(0).otherCheckIssues()).isEqualTo(Map.of("other", List.of(outbound)));
        assertThat(results.get(1).otherCheckIssues()).isEqualTo(Map.of("probe", List.of(traversal)));
        assertThat(results).allSatisfy(result -> assertThat(result.phases()).containsKey("audit"));
    }

    @Test
    void aSilentManifestDoesNotPassWhileAnIssueItsCheckMayHaveRaisedIsUnattributed() {
        IssueSummary shared = found("Shared name");
        ArmRunner runner = new ArmRunner((target, arm) -> closingArm(),
                Map.of(ReadinessProbeType.MCP_INITIALIZE, (endpoint, timeout) -> { }),
                attributedAudit(new ArmObservation(List.of(shared), 4, 0, new ProbeTally(9, 9), "details",
                        Map.of("probe", new CheckFindings(List.of(), new ProbeTally(5, 5), List.of(shared)),
                                CheckFindings.UNATTRIBUTED, new CheckFindings(List.of(shared), ProbeTally.NONE)))),
                CLOCK);

        ArmResult result = runner.run(ArmRun.asDeclared(target(), target().manifest().arms().get(1), issuePerRequest(),
                new NoAuthStrategy()));

        assertThat(result.passed()).isFalse();
        assertThat(result.verdict()).contains("may be this check's: Shared name");
    }

    @Test
    void failsEveryManifestOfASharedBuildThatCouldNotStart() {
        ArmRunner runner = new ArmRunner((target, arm) -> {
            throw new IllegalStateException("compose up failed");
        }, Map.of(), attributedAudit(null), CLOCK);
        ArmRun first = ArmRun.asDeclared(target(), target().manifest().arms().get(0), issuePerRequest(),
                new NoAuthStrategy());
        ArmRun second = ArmRun.asDeclared(otherTarget(), otherTarget().manifest().arms().get(0), checkWithId("other"),
                new NoAuthStrategy());

        List<ArmResult> results = runner.run(new SharedArmRun(first, List.of(first, second)));

        assertThat(results).extracting(ArmResult::check, ArmResult::passed)
                .containsExactly(tuple("probe", false),
                        tuple("other", false));
        assertThat(results).allSatisfy(result -> assertThat(result.error()).contains("compose up failed"));
    }

    private static ArmAudit attributedAudit(ArmObservation observation) {
        return new ArmAudit() {
            @Override
            public String mode() {
                return "burp";
            }

            @Override
            public ArmObservation audit(ArmRun run, ArmEndpoint endpoint,
                                        ArmTeardown teardown, ArmProgress progress) {
                return observation;
            }
        };
    }

    private static IssueSummary found(String name) {
        return new IssueSummary(name, AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM, "http://127.0.0.1:1",
                "detail");
    }

    private LoadedTarget otherTarget() {
        ManifestFixtures.writeValidTarget(root, "check-b", "target-two");
        return ManifestFixtures.loadOnly(root, "check-b", "target-two");
    }

    private static RequestCheck checkWithId(String id) {
        return new ProbeCheck() {
            @Override
            public CheckDescriptor descriptor() {
                return new CheckDescriptor(id, id, id, CheckKind.REQUEST, AuditIssueSeverity.INFORMATION,
                        AuditIssueConfidence.CERTAIN, true, List.of());
            }
        };
    }

    private CheckRunnerAudit audit;

    private ArmRunner runner(BaseRequestSource source, Runnable readiness) {
        return runner((target, arm) -> closingArm(), source, readiness);
    }

    private ArmRunner runner(TargetEnvironment environment, BaseRequestSource source, Runnable readiness) {
        ReadinessProbe probe = (endpoint, timeout) -> {
            readinessCalls.incrementAndGet();
            readinessObserver.run();
            readiness.run();
        };
        audit = new CheckRunnerAudit(source, readTimeout -> {
            readTimeouts.add(readTimeout);
            return armHttp;
        }, base -> List.of());
        return new ArmRunner(environment, Map.of(ReadinessProbeType.MCP_INITIALIZE, probe), audit, CLOCK);
    }

    private BaseRequestSource source(List<HttpRequestResponse> requests) {
        return (endpoint, seeds, http) -> new BaseRequestSet() {
            @Override
            public List<HttpRequestResponse> requests() {
                return requests;
            }

            @Override
            public int unansweredCount() {
                return 0;
            }

            @Override
            public void close() {
                requestsClosed.set(true);
            }
        };
    }

    private static HttpRequestResponse base() {
        return baseAnswering(200);
    }

    private static HttpRequestResponse baseAnswering(int status) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        when(base.response()).thenReturn(response);
        return base;
    }

    private static RequestCheck issuePerRequest() {
        return new ProbeCheck();
    }

    private static RequestCheck silentCheck() {
        return new ProbeCheck() {
            @Override
            public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
                http.sendRequest(mock(burp.api.montoya.http.message.requests.HttpRequest.class));
                return List.of();
            }
        };
    }

    private static Http answering() {
        Http http = mock(Http.class);
        when(http.sendRequest(org.mockito.ArgumentMatchers.any(burp.api.montoya.http.message.requests.HttpRequest.class)))
                .thenAnswer(invocation -> base());
        return http;
    }

    private static Http unanswered() {
        Http http = mock(Http.class);
        HttpRequestResponse noResponse = mock(HttpRequestResponse.class);
        when(http.sendRequest(org.mockito.ArgumentMatchers.any(burp.api.montoya.http.message.requests.HttpRequest.class)))
                .thenReturn(noResponse);
        return http;
    }

    private LoadedTarget target() {
        ManifestFixtures.writeValidTarget(root, "check-a", "target-one");
        return ManifestFixtures.loadOnly(root, "check-a", "target-one");
    }

    private static class ProbeCheck implements RequestCheck {

        @Override
        public CheckDescriptor descriptor() {
            return new CheckDescriptor("probe", "Probe", "Probe", CheckKind.REQUEST,
                    AuditIssueSeverity.INFORMATION, AuditIssueConfidence.CERTAIN, true, List.of());
        }

        @Override
        public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
            AuditIssue issue = mock(AuditIssue.class);
            when(issue.name()).thenReturn("Probe");
            return List.of(issue);
        }
    }
}
