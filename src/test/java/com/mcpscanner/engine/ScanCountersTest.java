package com.mcpscanner.engine;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointProvider;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.http.RequestCountingHttp;
import com.mcpscanner.checks.shared.scancontext.CurrentScan;
import com.mcpscanner.checks.shared.scancontext.ScanContext;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.MutableClock;
import com.mcpscanner.testutil.Polling;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.mcpscanner.engine.EngineFixtures.BOUND;
import static com.mcpscanner.engine.EngineFixtures.LABEL;
import static com.mcpscanner.engine.EngineFixtures.answeredBase;
import static com.mcpscanner.engine.EngineFixtures.unansweredBase;
import static com.mcpscanner.testutil.AwaitedFutures.valueWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScanCountersTest {

    private static final String URL = "https://mcp.example/mcp";
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final ScanInstrumentation COUNTING = (http, scanId, requestSent, cancelled) -> new InstrumentedScan() {
        private final Http counted = new RequestCountingHttp(http, requestSent);

        @Override
        public Http http() {
            return counted;
        }

        @Override
        public <T> T run(String testedRequest, Supplier<T> invocation) {
            return CurrentScan.within(Optional.of(new ScanContext(scanId)), invocation);
        }
    };

    private final Http http = mock(Http.class);
    private final AuditInsertionPointProvider insertionPoints = mock(AuditInsertionPointProvider.class);
    private final CountDownLatch gate = new CountDownLatch(1);
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void tearDown() {
        gate.countDown();
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    void countsBaseSendsAndEveryRequestAChecksSendsInsideOrOutsideBurpOnAnyThread() {
        McpScanEngine engine = engineOn(new SameThreadExecutorService());
        HttpRequestResponse unanswered = unansweredBase(URL);
        HttpRequestResponse answered = answeredBase(URL);
        when(http.sendRequest(any())).thenReturn(answered);
        registered(engine, ScanCheckType.PER_REQUEST).sending(2, 3);

        EngineScan scan = engine.scan(List.of(unanswered), LABEL);
        valueWithin(BOUND, scan.completion());

        assertThat(scan.snapshot().requests()).isEqualTo(6);
    }

    @Test
    void recordsACheckThatThrowsAsAnErrorWithItsCheckRequestFieldAndReason() {
        McpScanEngine engine = engineOn(new SameThreadExecutorService());
        AuditInsertionPoint point = namedPoint("params.arguments.path");
        when(insertionPoints.provideInsertionPoints(any())).thenReturn(List.of(point));
        registered(engine, ScanCheckType.PER_INSERTION_POINT).throwing(new IllegalStateException("boom"));

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);
        valueWithin(BOUND, scan.completion());

        assertThat(scan.snapshot().errors().total()).isEqualTo(1);
        assertThat(scan.snapshot().errors().recent()).containsExactly(new ScanError(NOW,
                Optional.of("PER_INSERTION_POINT"), URL, Optional.of("params.arguments.path"), "boom"));
    }

    @Test
    void recordsABaseRequestThatCannotBeSentAsAnErrorWithoutACheck() {
        McpScanEngine engine = engineOn(new SameThreadExecutorService());
        when(http.sendRequest(any())).thenThrow(new IllegalStateException("connection refused"));
        registered(engine, ScanCheckType.PER_REQUEST);

        EngineScan scan = engine.scan(List.of(unansweredBase(URL)), LABEL);
        valueWithin(BOUND, scan.completion());

        assertThat(scan.snapshot().errors().recent()).containsExactly(
                new ScanError(NOW, Optional.empty(), URL, Optional.empty(), "connection refused"));
    }

    @Test
    void aProbeWithNoResponseIsNotAnError() {
        McpScanEngine engine = engineOn(new SameThreadExecutorService());
        registered(engine, ScanCheckType.PER_REQUEST).sending(1, 0).returningNoResult();

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);
        valueWithin(BOUND, scan.completion());

        assertThat(scan.snapshot().errors().total()).isZero();
    }

    @Test
    void keepsTheLatestErrorsWhileCountingThemAll() {
        McpScanEngine engine = engineOn(new SameThreadExecutorService());
        registered(engine, ScanCheckType.PER_REQUEST).throwing(new IllegalStateException("boom"));
        List<HttpRequestResponse> bases = IntStream.range(0, ScanErrors.KEPT + 5)
                .mapToObj(index -> answeredBase(URL)).toList();

        EngineScan scan = engine.scan(bases, LABEL);
        valueWithin(BOUND, scan.completion());

        assertThat(scan.snapshot().errors().total()).isEqualTo(ScanErrors.KEPT + 5);
        assertThat(scan.snapshot().errors().recent()).hasSize(ScanErrors.KEPT);
    }

    @Test
    void showsEachInvocationAsRunningOnlyWhileItRuns() {
        pool = Executors.newFixedThreadPool(1);
        McpScanEngine engine = engineOn(pool);
        AuditInsertionPoint point = namedPoint("params.arguments.query");
        when(insertionPoints.provideInsertionPoints(any())).thenReturn(List.of(point));
        registered(engine, ScanCheckType.PER_INSERTION_POINT).blockingUntil(gate);

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);
        Polling.within(BOUND).until(() -> !scan.snapshot().nowRunning().isEmpty());

        assertThat(scan.snapshot().nowRunning()).containsExactly(new RunningInvocation("PER_INSERTION_POINT",
                "named " + URL, Optional.of("params.arguments.query")));

        gate.countDown();
        valueWithin(BOUND, scan.completion());
        assertThat(scan.snapshot().nowRunning()).isEmpty();
    }

    private McpScanEngine engineOn(ExecutorService executor) {
        return new McpScanEngine(http, insertionPoints, new FakeSiteMap(), new FixedWorkers(executor),
                new EngineEnvironment(new McpEventLog(null), new MutableClock(NOW), COUNTING,
                        request -> "named " + request.url(), scanId -> 0));
    }

    private static AuditInsertionPoint namedPoint(String name) {
        AuditInsertionPoint point = mock(AuditInsertionPoint.class);
        when(point.name()).thenReturn(name);
        return point;
    }

    private static FakeCheck registered(McpScanEngine engine, ScanCheckType type) {
        FakeCheck check = FakeCheck.named(type.name());
        engine.register(check, type);
        return check;
    }
}
