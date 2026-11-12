package com.mcpscanner.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.engine.EngineFixtures.LABEL;
import static org.assertj.core.api.Assertions.assertThat;

class ScanSnapshotTest {

    private static final Instant START = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void runningAndPausedScansAreActive() {
        assertThat(ScanStatus.RUNNING.active()).isTrue();
        assertThat(ScanStatus.PAUSED.active()).isTrue();
        assertThat(ScanStatus.FINISHED.active()).isFalse();
        assertThat(ScanStatus.CANCELLED.active()).isFalse();
    }

    @Test
    void aScanHasEndedOnceItHasAnEndTime() {
        assertThat(snapshot(ScanStatus.CANCELLED, Optional.empty(), 0).ended()).isFalse();
        assertThat(snapshot(ScanStatus.FINISHED, Optional.of(START), 0).ended()).isTrue();
    }

    @Test
    void anEndedScanAwaitsCollaboratorWhileItsPayloadsAreWatched() {
        assertThat(snapshot(ScanStatus.FINISHED, Optional.of(START), 2).awaitingCollaborator()).isTrue();
        assertThat(snapshot(ScanStatus.FINISHED, Optional.of(START), 0).awaitingCollaborator()).isFalse();
        assertThat(snapshot(ScanStatus.RUNNING, Optional.empty(), 2).awaitingCollaborator()).isFalse();
    }

    private static ScanSnapshot snapshot(ScanStatus status, Optional<Instant> endedAt, int watchedPayloads) {
        return new ScanSnapshot(1, LABEL, status, START, endedAt, new ScanProgress(1, 1, 0, 0, false, watchedPayloads),
                0, new ScanErrors(List.of(), 0), List.of(), new ScanFindings(List.of(), 0, Map.of()));
    }
}
