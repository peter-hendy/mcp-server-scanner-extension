package com.mcpscanner.engine;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.mcpscanner.engine.EngineFixtures.LABEL;
import static org.assertj.core.api.Assertions.assertThat;

class ScanHistoryTest {

    private static final Instant START = Instant.parse("2026-11-11T10:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final ScanHistory history = new ScanHistory(clock, scanId -> 0, new EngineLog(new McpEventLog(null)));

    @Test
    void listsScansNewestFirstWithTheirLabelsAndStartTimes() {
        EngineScan first = history.start(LABEL);
        clock.advance(Duration.ofMinutes(1));
        EngineScan second = history.start(new ScanLabel("Scan tools/call read_file", "h", "SSE", "User 2", "http://h"));

        assertThat(history.snapshots()).extracting(ScanSnapshot::id).containsExactly(second.id(), first.id());
        assertThat(history.snapshots().get(0).label().name()).isEqualTo("Scan tools/call read_file");
        assertThat(history.snapshots().get(1).startedAt()).isEqualTo(START);
    }

    @Test
    void aRunningScanIsRunningUntilItFinishes() {
        EngineScan scan = history.start(LABEL);
        clock.advance(Duration.ofSeconds(30));

        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.RUNNING);
        assertThat(scan.snapshot().endedAt()).isEmpty();

        scan.finish();

        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.FINISHED);
        assertThat(scan.snapshot().endedAt()).contains(START.plusSeconds(30));
    }

    @Test
    void aCancelledScanEndsCancelled() {
        EngineScan scan = history.start(LABEL);

        scan.cancel();
        scan.finish();

        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.CANCELLED);
    }

    @Test
    void tellsListenersOfEveryChange() {
        AtomicInteger changes = new AtomicInteger();
        history.addListener(changes::incrementAndGet);

        EngineScan scan = history.start(LABEL);
        scan.plan(2);
        scan.completed(List.of(), "ping");
        scan.finish();

        assertThat(changes.get()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void keepsAtMostTheBoundOfFinishedScansDroppingTheOldestFinished() {
        EngineScan running = history.start(LABEL);
        List<EngineScan> finished = IntStream.range(0, ScanHistory.FINISHED_KEPT + 2)
                .mapToObj(index -> finishedScan()).toList();

        assertThat(history.snapshots()).extracting(ScanSnapshot::id)
                .hasSize(ScanHistory.FINISHED_KEPT + 1)
                .contains(running.id())
                .doesNotContain(finished.get(0).id(), finished.get(1).id());
    }

    @Test
    void removesOnlyAFinishedScan() {
        EngineScan running = history.start(LABEL);
        EngineScan done = finishedScan();

        history.remove(running.id());
        history.remove(done.id());

        assertThat(history.snapshots()).extracting(ScanSnapshot::id).containsExactly(running.id());
    }

    @Test
    void clearingFinishedKeepsRunningScans() {
        finishedScan();
        EngineScan running = history.start(LABEL);
        finishedScan();
        AtomicInteger changes = new AtomicInteger();
        history.addListener(changes::incrementAndGet);

        history.clearFinished();

        assertThat(history.snapshots()).extracting(ScanSnapshot::id).containsExactly(running.id());
        assertThat(changes).hasValue(1);
    }

    @Test
    void findsAScanItStillHolds() {
        EngineScan scan = history.start(LABEL);

        assertThat(history.find(scan.id())).containsSame(scan);
        assertThat(history.find(scan.id() + 1)).isEmpty();
    }

    @Test
    void aThrowingListenerNeitherReachesTheScanNorStopsOtherListeners() {
        AtomicInteger heard = new AtomicInteger();
        history.addListener(() -> {
            throw new IllegalStateException("listener bug");
        });
        history.addListener(heard::incrementAndGet);

        EngineScan scan = history.start(LABEL);
        scan.plan(1);

        assertThat(heard.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void concurrentStartsAreListedNewestFirst() throws InterruptedException {
        List<Thread> starters = IntStream.range(0, 8).mapToObj(index -> new Thread(() ->
                IntStream.range(0, 200).forEach(start -> history.start(LABEL)))).toList();
        starters.forEach(Thread::start);
        for (Thread starter : starters) {
            starter.join();
        }

        List<Long> ids = history.snapshots().stream().map(ScanSnapshot::id).toList();
        assertThat(ids).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void evictsTheFinishedScanThatEndedEarliestNotTheOneThatStartedEarliest() {
        EngineScan longScan = history.start(LABEL);
        List<EngineScan> shortScans = IntStream.range(0, ScanHistory.FINISHED_KEPT)
                .mapToObj(index -> finishedScan()).toList();
        clock.advance(Duration.ofMinutes(5));

        longScan.finish();
        history.finished();

        assertThat(history.snapshots()).extracting(ScanSnapshot::id)
                .contains(longScan.id())
                .doesNotContain(shortScans.get(0).id());
    }

    @Test
    void aCancelledScanReportsCancelledWhileItsLastChecksFinish() {
        EngineScan scan = history.start(LABEL);

        scan.cancel();

        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.CANCELLED);
        assertThat(scan.snapshot().endedAt()).isEmpty();
    }

    @Test
    void cancellingAFinishedScanOrResumingAScanThatIsNotPausedTellsNoListener() {
        EngineScan scan = finishedScan();
        EngineScan running = history.start(LABEL);
        AtomicInteger changes = new AtomicInteger();
        history.addListener(changes::incrementAndGet);

        scan.cancel();
        running.resume();

        assertThat(changes).hasValue(0);
        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.FINISHED);
    }

    @Test
    void pausesResumesAndCancelsAScanByItsId() {
        EngineScan scan = history.start(LABEL);

        history.pause(scan.id());
        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.PAUSED);

        history.resume(scan.id());
        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.RUNNING);

        history.cancel(scan.id());
        assertThat(scan.snapshot().status()).isEqualTo(ScanStatus.CANCELLED);
    }

    @Test
    void controllingAnUnknownScanDoesNothing() {
        AtomicInteger changes = new AtomicInteger();
        history.addListener(changes::incrementAndGet);

        history.pause(99);
        history.resume(99);
        history.cancel(99);

        assertThat(changes).hasValue(0);
    }

    @Test
    void aRemovedListenerHearsNoMoreChanges() {
        AtomicInteger changes = new AtomicInteger();
        Runnable listener = changes::incrementAndGet;
        history.addListener(listener);
        history.removeListener(listener);

        history.start(LABEL);

        assertThat(changes).hasValue(0);
    }

    private EngineScan finishedScan() {
        EngineScan scan = history.start(LABEL);
        scan.finish();
        history.finished();
        return scan;
    }
}
