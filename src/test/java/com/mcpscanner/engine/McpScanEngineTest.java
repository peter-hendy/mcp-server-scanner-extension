package com.mcpscanner.engine;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointProvider;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.Polling;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.mcpscanner.engine.EngineFixtures.LABEL;
import static com.mcpscanner.engine.EngineFixtures.BOUND;
import static com.mcpscanner.engine.EngineFixtures.answeredBase;
import static com.mcpscanner.engine.EngineFixtures.insertionPoints;
import static com.mcpscanner.engine.EngineFixtures.issue;
import static com.mcpscanner.engine.EngineFixtures.unansweredBase;
import static com.mcpscanner.testutil.AwaitedFutures.valueWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpScanEngineTest {

    private static final String URL = "https://mcp.example/mcp";
    private static final String NAME = "Command injection";
    private static final List<String> SKIPS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final Http http = mock(Http.class);
    private final AuditInsertionPointProvider insertionPointProvider = mock(AuditInsertionPointProvider.class);
    private final FakeSiteMap siteMap = new FakeSiteMap();
    private final McpEventLog eventLog = new McpEventLog(null);
    private final CountDownLatch gate = new CountDownLatch(1);
    private final List<ExecutorService> pools = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        SKIPS.clear();
    }

    @AfterEach
    void tearDown() {
        gate.countDown();
        pools.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void recordsEachScanInItsHistoryUnderItsLabelUntilAndAfterItFinishes() {
        McpScanEngine engine = sameThreadEngine();
        registered(engine, ScanCheckType.PER_REQUEST);

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);
        finish(scan);

        assertThat(engine.history().snapshots()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.id()).isEqualTo(scan.id());
            assertThat(snapshot.label()).isEqualTo(LABEL);
            assertThat(snapshot.status()).isEqualTo(ScanStatus.FINISHED);
            assertThat(snapshot.progress().completed()).isEqualTo(1);
        });
    }

    @Test
    void keepsTheIssuesAScanAddedButNotDuplicatesTheSiteMapAlreadyHeld() {
        McpScanEngine engine = sameThreadEngine();
        AuditIssue found = issue(NAME, URL);
        registered(engine, ScanCheckType.PER_REQUEST).reporting(found)
                .consolidating(ConsolidationAction.KEEP_EXISTING);

        EngineScan first = engine.scan(List.of(answeredBase(URL)), LABEL);
        EngineScan rescan = engine.scan(List.of(answeredBase(URL)), LABEL);
        finish(rescan);

        assertThat(first.snapshot().issues().kept()).containsExactly(found);
        assertThat(rescan.snapshot().issues().total()).isZero();
    }

    @Test
    void creditsEachReturnedIssueWithTheNameOfTheRequestItsInvocationScanned() {
        McpScanEngine engine = sameThreadEngine();
        AuditIssue found = issue(NAME, URL);
        registered(engine, ScanCheckType.PER_REQUEST).reporting(found);

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);
        finish(scan);

        assertThat(scan.snapshot().issues().testedRequest(found)).contains(URL);
    }

    @Test
    void aThrowingHistoryListenerNeitherFailsAChecksNorLosesTheFinish() {
        McpScanEngine engine = sameThreadEngine();
        engine.history().addListener(() -> {
            throw new IllegalStateException("listener bug");
        });
        registered(engine, ScanCheckType.PER_REQUEST).reporting(issue(NAME, URL));

        ScanProgress progress = finish(engine.scan(List.of(answeredBase(URL)), LABEL));

        assertThat(progress).isEqualTo(new ScanProgress(1, 1, 0, 1, false));
        assertLogged(McpEventLog.Level.INFO, "Extension scan finished");
    }

    @Test
    void runsAnInsertionPointCheckOncePerInsertionPoint() {
        McpScanEngine engine = sameThreadEngine();
        HttpRequestResponse base = answeredBase(URL);
        List<AuditInsertionPoint> points = givenInsertionPoints(2);
        FakeCheck check = registered(engine, ScanCheckType.PER_INSERTION_POINT);

        finish(engine.scan(List.of(base), LABEL));

        assertThat(check.invocations()).extracting(FakeCheck.Invocation::base, FakeCheck.Invocation::insertionPoint)
                .containsExactly(tuple(base, points.get(0)), tuple(base, points.get(1)));
    }

    @Test
    void runsRequestAndHostChecksOncePerBaseWithoutAnInsertionPoint() {
        McpScanEngine engine = sameThreadEngine();
        HttpRequestResponse base = answeredBase(URL);
        givenInsertionPoints(1);
        FakeCheck request = registered(engine, ScanCheckType.PER_REQUEST);
        FakeCheck host = registered(engine, ScanCheckType.PER_HOST);

        finish(engine.scan(List.of(base), LABEL));

        assertThat(request.invocations()).extracting(FakeCheck.Invocation::base, FakeCheck.Invocation::insertionPoint)
                .containsExactly(tuple(base, null));
        assertThat(host.invocations()).extracting(FakeCheck.Invocation::base, FakeCheck.Invocation::insertionPoint)
                .containsExactly(tuple(base, null));
    }

    @Test
    void logsTheRequestsEveryCheckSentForABaseAtDebugLevel() {
        McpScanEngine engine = sameThreadEngine();
        HttpRequestResponse base = answeredBase(URL);
        givenInsertionPoints(1);
        registered(engine, ScanCheckType.PER_REQUEST).sendingProbes(2);
        registered(engine, ScanCheckType.PER_HOST).sendingProbes(1);

        finish(engine.scan(List.of(base), EngineFixtures.LABEL));

        verify(http, org.mockito.Mockito.times(3)).sendRequest(base.request());
        assertThat(eventLog.snapshot()).filteredOn(entry -> entry.level() == McpEventLog.Level.DEBUG)
                .extracting(McpEventLog.LogEntry::message)
                .anyMatch(message -> message.matches("Extension scan of " + URL + ": 3 requests, \\d+\\.\\d{2} s"));
    }

    @Test
    void sendsOnlyTheBasesThatHaveNoResponse() {
        McpScanEngine engine = sameThreadEngine();
        HttpRequestResponse answered = answeredBase(URL);
        HttpRequestResponse unanswered = unansweredBase(URL);
        HttpRequestResponse sent = answeredBase(URL);
        when(http.sendRequest(unanswered.request())).thenReturn(sent);
        FakeCheck check = registered(engine, ScanCheckType.PER_REQUEST);

        finish(engine.scan(List.of(answered, unanswered), LABEL));

        verify(http, never()).sendRequest(answered.request());
        assertThat(check.invocations()).extracting(FakeCheck.Invocation::base).containsExactly(answered, sent);
    }

    @Test
    void skipsAndLogsABaseWhoseSendFails() {
        McpScanEngine engine = sameThreadEngine();
        HttpRequestResponse failing = unansweredBase(URL);
        when(http.sendRequest(failing.request())).thenThrow(new IllegalStateException("connection refused"));
        FakeCheck check = registered(engine, ScanCheckType.PER_REQUEST);

        finish(engine.scan(List.of(failing), LABEL));

        assertThat(check.invocations()).isEmpty();
        assertLogged(McpEventLog.Level.WARN, "connection refused");
    }

    @Test
    void skipsABaseWhoseSendReturnsNoResponse() {
        McpScanEngine engine = sameThreadEngine();
        HttpRequestResponse unanswered = unansweredBase(URL);
        HttpRequestResponse stillUnanswered = unansweredBase(URL);
        when(http.sendRequest(unanswered.request())).thenReturn(stillUnanswered);
        FakeCheck check = registered(engine, ScanCheckType.PER_REQUEST);

        finish(engine.scan(List.of(unanswered), LABEL));

        assertThat(check.invocations()).isEmpty();
        assertLogged(McpEventLog.Level.WARN, URL);
    }

    @Test
    void skipsABaseWhoseInsertionPointsCannotBeProvided() {
        McpScanEngine engine = sameThreadEngine();
        HttpRequestResponse failing = answeredBase(URL);
        HttpRequestResponse healthy = answeredBase(URL);
        when(insertionPointProvider.provideInsertionPoints(failing)).thenThrow(new IllegalStateException("boom"));
        when(insertionPointProvider.provideInsertionPoints(healthy)).thenReturn(insertionPoints(1));
        FakeCheck check = registered(engine, ScanCheckType.PER_INSERTION_POINT);

        ScanProgress progress = finish(engine.scan(List.of(failing, healthy), LABEL));

        assertThat(check.invocations()).extracting(FakeCheck.Invocation::base).containsExactly(healthy);
        assertThat(progress).isEqualTo(new ScanProgress(1, 1, 0, 0, false));
        assertLogged(McpEventLog.Level.ERROR, "could not scan " + URL + ": boom");
    }

    @Test
    void isolatesAThrowingInvocationAndLeavesItsErrorToTheCheckAdapter() {
        McpScanEngine engine = sameThreadEngine();
        registered(engine, ScanCheckType.PER_REQUEST).throwing(new IllegalStateException("boom"));
        AuditIssue found = issue(NAME, URL);
        registered(engine, ScanCheckType.PER_REQUEST).reporting(found);

        ScanProgress progress = finish(engine.scan(List.of(answeredBase(URL)), LABEL));

        assertThat(siteMap.added()).containsExactly(found);
        assertThat(progress.completed()).isEqualTo(2);
        assertLogged(McpEventLog.Level.DEBUG, "boom");
        assertNothingLoggedAt(McpEventLog.Level.ERROR);
    }

    @Test
    void treatsAMissingAuditResultAsNoIssues() {
        McpScanEngine engine = sameThreadEngine();
        registered(engine, ScanCheckType.PER_REQUEST).returningNoResult();

        ScanProgress progress = finish(engine.scan(List.of(answeredBase(URL)), LABEL));

        assertThat(progress).isEqualTo(new ScanProgress(1, 1, 0, 0, false));
        assertThat(siteMap.added()).isEmpty();
    }

    @Test
    void reportsEveryIssueACheckFindsToTheSiteMap() {
        McpScanEngine engine = sameThreadEngine();
        AuditIssue first = issue(NAME, URL);
        AuditIssue second = issue("Path traversal", URL);
        registered(engine, ScanCheckType.PER_REQUEST).reporting(first, second);

        finish(engine.scan(List.of(answeredBase(URL)), LABEL));

        assertThat(siteMap.added()).containsExactly(first, second);
    }

    @Test
    void reportsAnIssueAgainAfterItIsDeletedFromTheSiteMap() {
        McpScanEngine engine = sameThreadEngine();
        AuditIssue found = issue(NAME, URL);
        registered(engine, ScanCheckType.PER_REQUEST).reporting(found)
                .consolidating(ConsolidationAction.KEEP_EXISTING);

        finish(engine.scan(List.of(answeredBase(URL)), LABEL));
        siteMap.delete(found);
        ScanProgress rescan = finish(engine.scan(List.of(answeredBase(URL)), LABEL));

        assertThat(siteMap.added()).containsExactly(found, found);
        assertThat(rescan.issuesReported()).isEqualTo(1);
    }

    @Test
    void completesWithTheCountsOfTheScan() {
        McpScanEngine engine = sameThreadEngine();
        givenInsertionPoints(2);
        registered(engine, ScanCheckType.PER_INSERTION_POINT).reporting(issue(NAME, URL));
        registered(engine, ScanCheckType.PER_REQUEST);

        ScanProgress progress = finish(engine.scan(List.of(answeredBase(URL)), LABEL));

        assertThat(progress).isEqualTo(new ScanProgress(3, 3, 0, 2, false));
    }

    @ParameterizedTest
    @CsvSource({"2, 1, '2 requests, 1 check'", "1, 2, '1 request, 2 checks'"})
    void logsTheStartAndFinishOfAScan(int requests, int checks, String counts) {
        McpScanEngine engine = sameThreadEngine();
        IntStream.range(0, checks).forEach(index -> registered(engine, ScanCheckType.PER_REQUEST));

        finish(engine.scan(IntStream.range(0, requests).mapToObj(index -> answeredBase(URL)).toList(), LABEL));

        assertLogged(McpEventLog.Level.INFO, "Extension scan started — " + counts);
        assertLogged(McpEventLog.Level.INFO, "Extension scan finished");
    }

    @Test
    void keepsTheStartAndFinishLinesThroughALogOverflow() {
        McpScanEngine engine = sameThreadEngine();
        registered(engine, ScanCheckType.PER_REQUEST);

        finish(engine.scan(List.of(answeredBase(URL)), LABEL));

        assertThat(eventLog.snapshot()).filteredOn(McpEventLog.LogEntry::kept)
                .extracting(McpEventLog.LogEntry::message)
                .anyMatch(message -> message.startsWith("Extension scan started"))
                .anyMatch(message -> message.startsWith("Extension scan finished"));
    }

    @Test
    void completesAScanStartedAfterShutdownAtOnceAsCancelled() {
        McpScanEngine engine = sameThreadEngine();
        FakeCheck check = registered(engine, ScanCheckType.PER_REQUEST);
        engine.shutdown();

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);

        assertThat(scan.completion()).isCompletedWithValue(new ScanProgress(0, 0, 0, 0, true));
        assertThat(check.invocations()).isEmpty();
        assertLogged(McpEventLog.Level.WARN, "Extension scan");
    }

    @Test
    void completesAScanWhoseInvocationsAreRejectedMidway() {
        McpScanEngine engine = engineOn(SameThreadExecutorService.acceptingOnly(2));
        givenInsertionPoints(3);
        FakeCheck check = registered(engine, ScanCheckType.PER_INSERTION_POINT);

        ScanProgress progress = finish(engine.scan(List.of(answeredBase(URL)), LABEL));

        assertThat(check.invocations()).hasSize(1);
        assertThat(progress).isEqualTo(new ScanProgress(3, 1, 2, 0, false));
        assertNothingLoggedAt(McpEventLog.Level.ERROR);
    }

    @Test
    void returnsBeforeTheScanFinishes() {
        McpScanEngine engine = pooledEngine(1);
        registered(engine, ScanCheckType.PER_REQUEST).blockingUntil(gate);

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);

        assertThat(scan.completion()).isNotDone();
    }

    @Test
    void cancelSkipsInvocationsThatHaveNotStarted() {
        McpScanEngine engine = pooledEngine(1);
        givenInsertionPoints(3);
        FakeCheck check = registered(engine, ScanCheckType.PER_INSERTION_POINT).blockingUntil(gate);

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);
        awaitInvocations(check, 1);
        scan.cancel();
        gate.countDown();
        ScanProgress progress = finish(scan);

        assertThat(check.invocations()).hasSize(1);
        assertThat(progress).isEqualTo(new ScanProgress(3, 1, 2, 0, true));
        assertLogged(McpEventLog.Level.INFO, "Extension scan cancelled");
        assertThat(SKIPS).containsExactly("PER_INSERTION_POINT 2 SCAN_CANCELLED");
    }

    @Test
    void cancelWhileABaseIsStillBeingSentRunsNoCheckOnIt() {
        McpScanEngine engine = pooledEngine(1);
        CountDownLatch sending = new CountDownLatch(1);
        HttpRequestResponse unanswered = unansweredBase(URL);
        HttpRequestResponse sent = answeredBase(URL);
        when(http.sendRequest(unanswered.request())).thenAnswer(invocation -> {
            sending.countDown();
            gate.await(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            return sent;
        });
        FakeCheck check = registered(engine, ScanCheckType.PER_REQUEST);

        EngineScan scan = engine.scan(List.of(unanswered), LABEL);
        awaitCountDown(sending);
        scan.cancel();
        gate.countDown();
        ScanProgress progress = finish(scan);

        assertThat(check.invocations()).isEmpty();
        assertThat(progress.completed()).isZero();
        assertThat(progress.cancelled()).isTrue();
    }

    @Test
    void shutdownWaitsForWorkInFlightAndCountsDroppedInvocationsAsSkipped() {
        ExecutorService pool = pool(1);
        McpScanEngine engine = engineOn(pool);
        givenInsertionPoints(2);
        FakeCheck check = registered(engine, ScanCheckType.PER_INSERTION_POINT).blockingUntil(gate);

        EngineScan scan = engine.scan(List.of(answeredBase(URL)), LABEL);
        awaitInvocations(check, 1);
        engine.shutdown();

        assertThat(scan.completion()).isCompletedWithValue(new ScanProgress(2, 1, 1, 0, true));
        assertThat(pool.isTerminated()).isTrue();
        assertThat(SKIPS).containsExactly("PER_INSERTION_POINT 1 EXTENSION_UNLOADED");
        assertNothingLoggedAt(McpEventLog.Level.ERROR);
    }

    @Test
    void reportsExactCountsAndAddsAnIdenticalIssueOnceUnderConcurrency() {
        McpScanEngine engine = pooledEngine(8);
        givenInsertionPoints(5);
        registered(engine, ScanCheckType.PER_INSERTION_POINT).reporting(issue(NAME, URL))
                .consolidating(ConsolidationAction.KEEP_EXISTING);
        registered(engine, ScanCheckType.PER_REQUEST);
        List<HttpRequestResponse> bases = IntStream.range(0, 40).mapToObj(index -> answeredBase(URL)).toList();

        ScanProgress progress = finish(engine.scan(bases, LABEL));

        assertThat(progress).isEqualTo(new ScanProgress(240, 240, 0, 1, false));
        assertThat(siteMap.added()).hasSize(1);
    }

    private McpScanEngine sameThreadEngine() {
        return engineOn(new SameThreadExecutorService());
    }

    private McpScanEngine pooledEngine(int threads) {
        return engineOn(pool(threads));
    }

    private ExecutorService pool(int threads) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        pools.add(pool);
        return pool;
    }

    private McpScanEngine engineOn(ExecutorService executor) {
        return new McpScanEngine(http, insertionPointProvider, siteMap, new FixedWorkers(executor), EngineFixtures.environment(eventLog));
    }

    private List<AuditInsertionPoint> givenInsertionPoints(int count) {
        List<AuditInsertionPoint> points = insertionPoints(count);
        when(insertionPointProvider.provideInsertionPoints(any())).thenReturn(points);
        return points;
    }

    private static FakeCheck registered(McpScanEngine engine, ScanCheckType type) {
        FakeCheck check = FakeCheck.named(type.name());
        engine.register(check, type, (invocations, cause) -> SKIPS.add(type.name() + " " + invocations + " " + cause));
        return check;
    }

    private static ScanProgress finish(EngineScan scan) {
        return valueWithin(BOUND, scan.completion());
    }

    private static void awaitInvocations(FakeCheck check, int count) {
        Polling.within(BOUND).until(() -> check.invocations().size() >= count);
    }

    private static void awaitCountDown(CountDownLatch latch) {
        Polling.within(BOUND).until(() -> latch.getCount() == 0);
    }

    private void assertLogged(McpEventLog.Level level, String fragment) {
        assertThat(eventLog.snapshot())
                .anyMatch(entry -> entry.level() == level && entry.message().contains(fragment));
    }

    private void assertNothingLoggedAt(McpEventLog.Level level) {
        assertThat(eventLog.snapshot()).noneMatch(entry -> entry.level() == level);
    }
}
