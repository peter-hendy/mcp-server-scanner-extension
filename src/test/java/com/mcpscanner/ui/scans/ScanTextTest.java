package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.engine.RunningInvocation;
import com.mcpscanner.engine.ScanErrors;
import com.mcpscanner.engine.ScanProgress;
import com.mcpscanner.engine.ScanSnapshot;
import com.mcpscanner.engine.ScanStatus;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.ui.scans.ScanSnapshots.LABEL;
import static com.mcpscanner.ui.scans.ScanSnapshots.START;
import static org.assertj.core.api.Assertions.assertThat;

class ScanTextTest {

    private final ScanText text = new ScanText(ZoneOffset.UTC);

    @Test
    void summarisesAScanOnOneLine() {
        ScanSnapshot snapshot = new ScanSnapshot(1, LABEL, ScanStatus.RUNNING, START, Optional.empty(),
                new ScanProgress(100, 58, 0, 4, false, 0), 1284, new ScanErrors(List.of(), 3), List.of(),
                ScanSnapshots.findings(4, Map.of(AuditIssueSeverity.HIGH, 1, AuditIssueSeverity.MEDIUM, 2,
                        AuditIssueSeverity.INFORMATION, 1)));

        assertThat(text.summary(snapshot)).isEqualTo("58% · 1,284 requests · 3 errors · 4 issues (1 High, 2 Medium, 1 Info)");
    }

    @Test
    void aScanWithNoIssuesOrPlannedWorkReadsZero() {
        ScanSnapshot snapshot = ScanSnapshots.snapshot(1, ScanStatus.RUNNING, new ScanProgress(0, 0, 0, 0, false));

        assertThat(text.summary(snapshot)).isEqualTo("0% · 0 requests · 0 errors · 0 issues");
    }

    @Test
    void singularNounsForOneOfEach() {
        assertThat(text.requests(1)).isEqualTo("1 request");
        assertThat(text.errors(1)).isEqualTo("1 error");
        assertThat(text.issues(ScanSnapshots.findings(1, Map.of(AuditIssueSeverity.LOW, 1))))
                .isEqualTo("1 issue (1 Low)");
    }

    @Test
    void namesEachStatusAndWhereACancelledScanStopped() {
        assertThat(text.status(ScanSnapshots.snapshot(1, ScanStatus.RUNNING, progress(10, 4)))).isEqualTo("Running");
        assertThat(text.status(ScanSnapshots.snapshot(1, ScanStatus.PAUSED, progress(10, 4)))).isEqualTo("Paused");
        assertThat(text.status(ScanSnapshots.finished(1))).isEqualTo("Finished");
        assertThat(text.status(ScanSnapshots.snapshot(1, ScanStatus.CANCELLED, progress(8, 3))))
                .isEqualTo("Cancelled at 37%");
    }

    @Test
    void countsCheckRunsDoneOfPlanned() {
        assertThat(text.progress(ScanSnapshots.snapshot(1, ScanStatus.RUNNING, progress(2000, 1500))))
                .isEqualTo("1,500 of 2,000 check runs");
    }

    @Test
    void describesARunningInvocationWithAndWithoutAField() {
        assertThat(text.nowRunning(new RunningInvocation("File traversal", "tools/call read_file",
                Optional.of("params.arguments.path")))).isEqualTo("File traversal — tools/call read_file · params.arguments.path");
        assertThat(text.nowRunning(new RunningInvocation("Session lifecycle", "ping", Optional.empty())))
                .isEqualTo("Session lifecycle — ping");
    }

    @Test
    void namesWhereAndAsWhomTheScanRan() {
        assertThat(text.connection(LABEL)).isEqualTo("mcp.example · Streamable HTTP · as User 1");
    }

    @Test
    void showsStartAndEndTimes() {
        assertThat(text.times(ScanSnapshots.running(1))).isEqualTo("Started 10:00:00");
        assertThat(text.times(ScanSnapshots.finished(1))).isEqualTo("10:00:00 – 10:01:05");
    }

    @Test
    void countsCollaboratorPayloadsStillWatched() {
        assertThat(text.watched(1)).isEqualTo("1 Collaborator payload still watched");
        assertThat(text.watched(12)).isEqualTo("12 Collaborator payloads still watched");
    }

    private static ScanProgress progress(int planned, int completed) {
        return new ScanProgress(planned, completed, 0, 0, false);
    }
}
