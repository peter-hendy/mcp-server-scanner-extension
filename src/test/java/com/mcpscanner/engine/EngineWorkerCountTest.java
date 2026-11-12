package com.mcpscanner.engine;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointProvider;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.scancheck.ActiveScanCheck;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.Polling;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mcpscanner.engine.EngineFixtures.BOUND;
import static com.mcpscanner.engine.EngineFixtures.LABEL;
import static com.mcpscanner.engine.EngineFixtures.answeredBase;
import static com.mcpscanner.engine.EngineFixtures.insertionPoints;
import static com.mcpscanner.testutil.AwaitedFutures.valueWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineWorkerCountTest {

    private static final int INVOCATIONS = 12;
    private static final Duration STEADY = Duration.ofMillis(200);

    private final AtomicInteger workerCount = new AtomicInteger(1);
    private McpScanEngine engine;
    private CountingCheck check;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        AuditInsertionPointProvider insertionPointProvider = mock(AuditInsertionPointProvider.class);
        List<AuditInsertionPoint> points = insertionPoints(INVOCATIONS);
        when(insertionPointProvider.provideInsertionPoints(any())).thenReturn(points);
        engine = new McpScanEngine(mock(Http.class), insertionPointProvider, new FakeSiteMap(),
                ResizableWorkers.sizedBy(workerCount::get, Executors.defaultThreadFactory()),
                EngineFixtures.environment(new McpEventLog(null)));
    }

    @AfterEach
    void tearDown() {
        if (check != null) {
            check.release();
        }
        engine.shutdown();
    }

    @Test
    void runsAsManyInvocationsAtOnceAsTheWorkerCountWhenTheScanStarted() {
        workerCount.set(3);
        check = registered();

        EngineScan scan = engine.scan(List.of(answeredBase("https://mcp.example/mcp")), LABEL);

        check.holdsSteadyAt(3);
        check.release();
        assertThat(valueWithin(BOUND, scan.completion()).completed()).isEqualTo(INVOCATIONS);
        assertThat(check.peak()).isEqualTo(3);
    }

    @Test
    void appliesAChangedWorkerCountWhenTheNextScanStarts() {
        workerCount.set(2);
        check = registered();
        EngineScan first = engine.scan(List.of(answeredBase("https://mcp.example/a")), LABEL);
        check.holdsSteadyAt(2);
        check.release();
        valueWithin(BOUND, first.completion());

        workerCount.set(5);
        CountingCheck next = registered();
        EngineScan second = engine.scan(List.of(answeredBase("https://mcp.example/b")), LABEL);

        next.holdsSteadyAt(5);
        next.release();
        valueWithin(BOUND, second.completion());
    }

    @Test
    void neitherResizesNorInterruptsARunningScanUntilAnotherScanStarts() {
        workerCount.set(3);
        check = registered();
        EngineScan scan = engine.scan(List.of(answeredBase("https://mcp.example/mcp")), LABEL);
        check.awaitRunning(3);

        workerCount.set(1);
        check.holdsSteadyAt(3);
        check.release();

        ScanProgress progress = valueWithin(BOUND, scan.completion());
        assertThat(progress.completed()).isEqualTo(INVOCATIONS);
        assertThat(progress.skipped()).isZero();
        assertThat(check.interrupted()).isZero();
    }

    @Test
    void shrinkingWhenAnotherScanStartsLetsRunningInvocationsFinishUninterrupted() {
        workerCount.set(3);
        check = registered();
        EngineScan first = engine.scan(List.of(answeredBase("https://mcp.example/a")), LABEL);
        check.awaitRunning(3);

        workerCount.set(1);
        EngineScan second = engine.scan(List.of(answeredBase("https://mcp.example/b")), LABEL);
        check.release();

        assertThat(valueWithin(BOUND, first.completion()).skipped()).isZero();
        assertThat(valueWithin(BOUND, second.completion()).skipped()).isZero();
        assertThat(check.interrupted()).isZero();
    }

    private CountingCheck registered() {
        CountingCheck registered = new CountingCheck();
        engine.register(registered, ScanCheckType.PER_INSERTION_POINT);
        return registered;
    }

    private static final class CountingCheck implements ActiveScanCheck {

        private final CountDownLatch gate = new CountDownLatch(1);
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();
        private final AtomicInteger interrupted = new AtomicInteger();

        @Override
        public String checkName() {
            return "counting";
        }

        @Override
        public AuditResult doCheck(HttpRequestResponse base, AuditInsertionPoint insertionPoint, Http http) {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                gate.await(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interruption) {
                interrupted.incrementAndGet();
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
            return AuditResult.auditResult(List.of());
        }

        @Override
        public ConsolidationAction consolidateIssues(AuditIssue existingIssue, AuditIssue newIssue) {
            return ConsolidationAction.KEEP_BOTH;
        }

        void awaitRunning(int count) {
            Polling.within(BOUND).until(() -> running.get() == count);
        }

        void holdsSteadyAt(int count) {
            awaitRunning(count);
            Polling.within(BOUND).during(STEADY).until(() -> running.get() == count);
        }

        void release() {
            gate.countDown();
        }

        int peak() {
            return peak.get();
        }

        int interrupted() {
            return interrupted.get();
        }
    }
}
