package com.mcpscanner.engine;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.scancheck.ActiveScanCheck;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointProvider;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.Polling;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static com.mcpscanner.engine.EngineFixtures.BOUND;
import static com.mcpscanner.engine.EngineFixtures.LABEL;
import static com.mcpscanner.engine.EngineFixtures.answeredBase;
import static com.mcpscanner.engine.EngineFixtures.insertionPoints;
import static com.mcpscanner.testutil.AwaitedFutures.valueWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PauseResumeTest {

    private static final String URL = "https://mcp.example/mcp";

    private final Http http = mock(Http.class);
    private final AuditInsertionPointProvider insertionPoints = mock(AuditInsertionPointProvider.class);
    private final CountDownLatch gate = new CountDownLatch(1);
    private final List<Throwable> uncaught = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(1, work -> {
        Thread worker = new Thread(work);
        worker.setUncaughtExceptionHandler((thread, failure) -> uncaught.add(failure));
        return worker;
    });
    private McpScanEngine engine;
    private FakeCheck check;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        when(insertionPoints.provideInsertionPoints(any())).thenReturn(insertionPoints(3));
        engine = new McpScanEngine(http, insertionPoints, new FakeSiteMap(), new FixedWorkers(pool),
                EngineFixtures.environment(new McpEventLog(null)));
        check = FakeCheck.named("gated").blockingUntil(gate);
        engine.register(check, ScanCheckType.PER_INSERTION_POINT);
    }

    @AfterEach
    void tearDown() {
        gate.countDown();
        pool.shutdownNow();
    }

    @Test
    void aPausedScanLetsItsRunningCheckFinishButStartsNoMore() {
        EngineScan scan = pausedAfterFirstInvocationStarted();

        gate.countDown();
        Polling.within(BOUND).until(() -> scan.snapshot().progress().completed() == 1);

        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.PAUSED);
        assertThat(check.invocations()).hasSize(1);
        assertThat(scan.completion()).isNotDone();
    }

    @Test
    void aPausedScanDoesNotHoldThePoolFromOtherScans() {
        EngineScan paused = pausedAfterFirstInvocationStarted();
        gate.countDown();

        EngineScan other = engine.scan(List.of(answeredBase(URL)), LABEL);

        assertThat(valueWithin(BOUND, other.completion()).completed()).isEqualTo(3);
        assertThat(paused.snapshot().status()).isEqualTo(ScanStatus.PAUSED);
    }

    @Test
    void resumingRunsEveryParkedCheck() {
        EngineScan scan = pausedAfterFirstInvocationStarted();
        gate.countDown();
        Polling.within(BOUND).until(() -> scan.snapshot().progress().completed() == 1);

        scan.resume();

        assertThat(valueWithin(BOUND, scan.completion())).isEqualTo(new ScanProgress(3, 3, 0, 0, false));
        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.FINISHED);
    }

    @Test
    void cancellingAPausedScanDropsItsParkedChecksAsSkipped() {
        EngineScan scan = pausedAfterFirstInvocationStarted();
        gate.countDown();
        Polling.within(BOUND).until(() -> scan.snapshot().progress().completed() == 1);

        scan.cancel();

        assertThat(valueWithin(BOUND, scan.completion())).isEqualTo(new ScanProgress(3, 1, 2, 0, true));
        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.CANCELLED);
    }

    @Test
    void unloadingWhilePausedDropsParkedChecksAsSkipped() {
        EngineScan scan = pausedAfterFirstInvocationStarted();
        gate.countDown();
        Polling.within(BOUND).until(() -> scan.snapshot().progress().completed() == 1);

        engine.shutdown();

        assertThat(scan.completion()).isCompletedWithValue(new ScanProgress(3, 1, 2, 0, true));
    }

    @Test
    void pausingAFinishedScanChangesNothing() {
        gate.countDown();
        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);
        valueWithin(BOUND, scan.completion());

        scan.pause();

        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.FINISHED);
    }

    @Test
    void aBaseRequestStillQueuedWhenPausedIsNotSentUntilResumed() {
        HttpRequestResponse unanswered = EngineFixtures.unansweredBase(URL);
        HttpRequestResponse answered = answeredBase(URL);
        when(http.sendRequest(any())).thenReturn(answered);
        EngineScan blocker = engine.scan(List.of(answeredBase(URL)), LABEL);
        Polling.within(BOUND).until(() -> check.invocations().size() == 1);
        EngineScan queued = engine.scan(List.of(unanswered), LABEL);

        queued.pause();
        gate.countDown();
        valueWithin(BOUND, blocker.completion());

        verify(http, never()).sendRequest(any());
        assertThat(queued.snapshot().progress().planned()).isZero();

        queued.resume();

        assertThat(valueWithin(BOUND, queued.completion())).isEqualTo(new ScanProgress(3, 3, 0, 0, false));
        verify(http).sendRequest(unanswered.request());
    }

    @Test
    void concurrentPauseResumeAndCancelAlwaysEndTheScanWithEveryCheckAccountedFor() throws InterruptedException {
        gate.countDown();
        when(insertionPoints.provideInsertionPoints(any())).thenReturn(insertionPoints(50));
        EngineScan scan = engine.scan(IntStream.range(0, 20).mapToObj(index -> answeredBase(URL)).toList(), LABEL);
        List<Thread> togglers = IntStream.range(0, 4).mapToObj(index -> new Thread(() ->
                IntStream.range(0, 500).forEach(round -> {
                    scan.pause();
                    scan.resume();
                }))).toList();
        togglers.forEach(Thread::start);
        scan.cancel();
        for (Thread toggler : togglers) {
            toggler.join();
        }

        ScanProgress progress = valueWithin(BOUND, scan.completion());
        assertThat(progress.completed() + progress.skipped()).isEqualTo(progress.planned());
        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.CANCELLED);
    }

    @Test
    void aCheckThatThrowsAnErrorIsSkippedAndTheScanStillFinishes() {
        gate.countDown();
        engine.register(new ErrorThrowingCheck(), ScanCheckType.PER_REQUEST);

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);

        ScanProgress progress = valueWithin(BOUND, scan.completion());
        assertThat(progress.planned()).isEqualTo(progress.completed() + progress.skipped());
        assertThat(progress.skipped()).isEqualTo(1);
        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.FINISHED);
        Polling.within(BOUND).until(() -> !uncaught.isEmpty());
        assertThat(uncaught).singleElement().isInstanceOf(AssertionError.class);
    }

    private EngineScan pausedAfterFirstInvocationStarted() {
        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);
        Polling.within(BOUND).until(() -> check.invocations().size() == 1);
        scan.pause();
        return scan;
    }

    private static final class ErrorThrowingCheck implements ActiveScanCheck {
        @Override
        public String checkName() {
            return "error";
        }

        @Override
        public AuditResult doCheck(HttpRequestResponse base, AuditInsertionPoint insertionPoint, Http http) {
            throw new AssertionError("check bug");
        }

        @Override
        public ConsolidationAction consolidateIssues(AuditIssue existingIssue, AuditIssue newIssue) {
            return ConsolidationAction.KEEP_BOTH;
        }
    }
}
