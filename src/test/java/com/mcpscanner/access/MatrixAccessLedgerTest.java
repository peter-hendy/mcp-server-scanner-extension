package com.mcpscanner.access;

import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessFinding;
import com.mcpscanner.checks.api.AccessOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.mcpscanner.access.AccessFixtures.ALICE;
import static com.mcpscanner.access.AccessFixtures.BOB;
import static com.mcpscanner.access.AccessFixtures.readOnlyTool;
import static com.mcpscanner.access.AccessFixtures.resource;
import static com.mcpscanner.access.AccessFixtures.resourceKey;
import static com.mcpscanner.access.AccessFixtures.tool;
import static com.mcpscanner.access.AccessFixtures.toolKey;
import static org.assertj.core.api.Assertions.assertThat;

class MatrixAccessLedgerTest {

    private final AccessMatrix matrix = new AccessFixtures().compare(BOB)
            .lists(ALICE, List.of(tool("delete"), readOnlyTool("search")), List.of(resource("notes://a")))
            .lists(BOB, List.of(tool("delete")), List.of()).matrix();
    private final WriteReplayConfirmations confirmations = new WriteReplayConfirmations();
    private final MatrixAccessLedger ledger = new MatrixAccessLedger(matrix, confirmations);

    @Test
    void resourcesAndReadOnlyToolsAreAlwaysReplayed() {
        assertThat(ledger.permitsReplay(resourceKey("notes://a"))).isTrue();
        assertThat(ledger.permitsReplay(resourceKey("notes://unlisted"))).isTrue();
        assertThat(ledger.permitsReplay(toolKey("search"))).isTrue();
    }

    @Test
    void writeToolsAreReplayedOnlyOnceConfirmed() {
        assertThat(ledger.permitsReplay(toolKey("delete"))).isFalse();

        confirmations.confirm(List.of(tool("delete")));

        assertThat(ledger.permitsReplay(toolKey("delete"))).isTrue();
    }

    @Test
    void onlyAConfirmedWriteToolReplaysAWrite() {
        confirmations.confirm(List.of(tool("delete")));

        assertThat(ledger.replaysWrite(toolKey("delete"))).isTrue();
        assertThat(ledger.replaysWrite(toolKey("search"))).isFalse();
        assertThat(ledger.replaysWrite(resourceKey("notes://a"))).isFalse();
        assertThat(ledger.replaysWrite(toolKey("unlisted"))).isFalse();
    }

    @Test
    void unknownToolsAreTreatedAsWrites() {
        assertThat(ledger.permitsReplay(toolKey("unlisted"))).isFalse();
    }

    @Test
    void clearingConfirmationsWithdrawsPermission() {
        confirmations.confirm(List.of(tool("delete")));
        confirmations.clear();

        assertThat(ledger.permitsReplay(toolKey("delete"))).isFalse();
    }

    @Test
    void expectationComesFromTheMatrix() {
        assertThat(ledger.expectationFor(toolKey("search"))).isEqualTo(AccessExpectation.OWNER_ONLY);
        assertThat(ledger.expectationFor(toolKey("unlisted"))).isEqualTo(AccessExpectation.ANY_SIGNED_IN);
    }

    @Test
    void outcomesAndFindingsRecordIntoTheMatrix() {
        ledger.record(toolKey("delete"), BOB.id(), AccessOutcome.SAME_RESULT);
        ledger.recordAnonymous(toolKey("delete"), AccessOutcome.REFUSED);
        ledger.recordFinding(toolKey("delete"), BOB.id(), new AccessFinding(AccessFinding.Kind.OPERATION_LEVEL, "Operation-level: test"));

        AccessRow row = matrix.row(toolKey("delete")).orElseThrow();
        assertThat(row.cell(BOB.id()).outcome()).contains(AccessOutcome.SAME_RESULT);
        assertThat(row.cell(BOB.id()).anomaly()).isTrue();
        assertThat(row.anonymous().outcome()).contains(AccessOutcome.REFUSED);
    }

    @Test
    void concurrentRecordsFromScannerThreadsAllLand() throws Exception {
        List<String> names = IntStream.range(0, 200).mapToObj(index -> "item-" + index).toList();
        AccessMatrix busy = new AccessFixtures().compare(BOB)
                .lists(ALICE, names.stream().map(AccessFixtures::tool).toList(), List.of()).matrix();
        MatrixAccessLedger busyLedger = new MatrixAccessLedger(busy, confirmations);
        ExecutorService scannerThreads = Executors.newFixedThreadPool(8);
        names.forEach(name -> scannerThreads.execute(() -> {
            busyLedger.record(toolKey(name), BOB.id(), AccessOutcome.REFUSED);
            busyLedger.recordAnonymous(toolKey(name), AccessOutcome.REFUSED);
        }));
        scannerThreads.shutdown();
        assertThat(scannerThreads.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(busy.snapshot().rows()).hasSize(200).allSatisfy(row -> {
            assertThat(row.cell(BOB.id()).outcome()).contains(AccessOutcome.REFUSED);
            assertThat(row.anonymous().outcome()).contains(AccessOutcome.REFUSED);
        });
    }
}
