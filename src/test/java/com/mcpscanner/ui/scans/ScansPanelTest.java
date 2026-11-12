package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.engine.RunningInvocation;
import com.mcpscanner.engine.ScanError;
import com.mcpscanner.engine.ScanProgress;
import com.mcpscanner.engine.ScanStatus;
import com.mcpscanner.testutil.ManualReadScheduler;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.widgets.DotIcon;
import com.mcpscanner.ui.widgets.HyperlinkLabel;
import com.mcpscanner.ui.widgets.SeverityColors;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import java.awt.Component;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.mcpscanner.ui.scans.ScanSnapshots.START;
import static org.assertj.core.api.Assertions.assertThat;

class ScansPanelTest {

    private final ManualReadScheduler scheduler = new ManualReadScheduler();
    private final FakeScans scans = new FakeScans();
    private final FakeEditors editors = new FakeEditors();
    private final List<TargetLocation> shownInTarget = new ArrayList<>();
    private final ScansPanel panel = new ScansPanel(new ScansServices(scans, scans, shownInTarget::add, editors),
            scheduler.reads(), ZoneOffset.UTC);

    @Test
    void showsOneCardPerScanNewestFirst() {
        publish(ScanSnapshots.running(2), ScanSnapshots.finished(1));

        assertThat(cards()).extracting(Component::getName).containsExactly("scan-card-2", "scan-card-1");
    }

    @Test
    void saysSoWhenThereAreNoScans() {
        publish();
        assertThat(Find.named(panel, JLabel.class, ScansPanel.EMPTY_STATE).isVisible()).isTrue();

        publish(ScanSnapshots.running(1));
        assertThat(Find.named(panel, JLabel.class, ScansPanel.EMPTY_STATE).isVisible()).isFalse();
    }

    @Test
    void aRunningScanStartsOpenAndAFinishedScanStartsClosedWithItsSummary() {
        publish(ScanSnapshots.running(2), ScanSnapshots.finished(1));

        assertThat(expanded(card(2))).isTrue();
        assertThat(label(card(2), ScanCard.SUMMARY).isVisible()).isFalse();
        assertThat(expanded(card(1))).isFalse();
        assertThat(label(card(1), ScanCard.SUMMARY).isVisible()).isTrue();
        assertThat(label(card(1), ScanCard.SUMMARY).getText()).isEqualTo("100% · 3 requests · 0 errors · 0 issues");
    }

    @Test
    void theArrowOpensAndClosesACardAndItStaysThatWayAcrossUpdates() {
        publish(ScanSnapshots.running(1));
        ScanCard card = card(1);

        button(card, ScanCard.EXPAND).doClick();
        publish(ScanSnapshots.snapshot(1, ScanStatus.RUNNING, new ScanProgress(10, 6, 0, 0, false)));

        assertThat(card(1)).isSameAs(card);
        assertThat(expanded(card)).isFalse();
        assertThat(label(card, ScanCard.SUMMARY).getText()).startsWith("60%");
        button(card, ScanCard.EXPAND).doClick();
        assertThat(expanded(card)).isTrue();
    }

    @Test
    void theHeaderNamesTheScanItsStatusConnectionAndTimes() {
        publish(ScanSnapshots.finished(1));

        assertThat(label(card(1), ScanCard.NAME).getText()).isEqualTo("Scan 14 requests");
        assertThat(Find.all(card(1), Chip.class)).singleElement().extracting(JLabel::getText).isEqualTo("Finished");
        assertThat(label(card(1), ScanCard.DETAILS).getText())
                .isEqualTo("mcp.example · Streamable HTTP · as User 1 · 10:00:00 – 10:01:05");
    }

    @Test
    void pauseResumeAndCancelControlTheScanOffTheCallersThread() {
        publish(ScanSnapshots.running(1));
        JButton pause = button(card(1), ScanCard.PAUSE);

        assertThat(pause.getText()).isEqualTo("Pause");
        pause.doClick();
        assertThat(scans.calls).isEmpty();
        scheduler.runAll();
        publish(ScanSnapshots.snapshot(1, ScanStatus.PAUSED, new ScanProgress(10, 4, 0, 0, false)));
        assertThat(pause.getText()).isEqualTo("Resume");
        pause.doClick();
        button(card(1), ScanCard.CANCEL).doClick();
        scheduler.runAll();

        assertThat(scans.calls).containsExactly("pause 1", "resume 1", "cancel 1");
        assertThat(button(card(1), ScanCard.REMOVE).isVisible()).isFalse();
    }

    @Test
    void resumeClickedBeforeThePauseShowsUpStillResumes() {
        publish(ScanSnapshots.running(1));
        JButton pause = button(card(1), ScanCard.PAUSE);

        pause.doClick();
        publish(ScanSnapshots.snapshot(1, ScanStatus.RUNNING, new ScanProgress(10, 5, 0, 0, false)));
        assertThat(pause.getText()).isEqualTo("Resume");
        pause.doClick();
        scheduler.runAll();

        assertThat(scans.calls).containsExactly("pause 1", "resume 1");
        assertThat(pause.getText()).isEqualTo("Pause");
    }

    @Test
    void aFinishedScanCanOnlyBeRemoved() {
        publish(ScanSnapshots.finished(1));

        assertThat(button(card(1), ScanCard.PAUSE).isVisible()).isFalse();
        assertThat(button(card(1), ScanCard.CANCEL).isVisible()).isFalse();
        button(card(1), ScanCard.REMOVE).doClick();
        scheduler.runAll();

        assertThat(scans.calls).containsExactly("remove 1");
    }

    @Test
    void clearFinishedIsOfferedOnlyWhenAScanHasEnded() {
        publish(ScanSnapshots.running(2));
        JButton clear = SwingTree.button(panel, "Clear finished");
        assertThat(clear.isEnabled()).isFalse();

        publish(ScanSnapshots.running(2), ScanSnapshots.finished(1));
        clear.doClick();
        scheduler.runAll();

        assertThat(scans.calls).containsExactly("clear finished");
    }

    @Test
    void aRemovedScanLosesItsCard() {
        publish(ScanSnapshots.running(2), ScanSnapshots.finished(1));

        publish(ScanSnapshots.running(2));

        assertThat(cards()).extracting(Component::getName).containsExactly("scan-card-2");
    }

    @Test
    void theBodyShowsProgressCountsAndWhatIsRunningNow() {
        publish(ScanSnapshots.withNowRunning(1, List.of(
                new RunningInvocation("File traversal", "tools/call read_file", Optional.of("params.arguments.path")),
                new RunningInvocation("Session lifecycle", "ping", Optional.empty()))));

        ScanCard card = card(1);
        assertThat(Find.named(card, JProgressBar.class, ScanCardBody.PROGRESS).getString())
                .isEqualTo("4 of 10 check runs");
        assertThat(label(card, CountsLine.REQUESTS).getText()).isEqualTo("0 requests");
        assertThat(label(card, CountsLine.ERRORS_LINK).getText()).isEqualTo("0 errors");
        assertThat(label(card, CountsLine.ISSUE_COUNT).getText()).isEqualTo("0 issues");
        assertThat(Find.named(card, JPanel.class, NowRunningSection.NAME).isVisible()).isTrue();
        assertThat(Find.all(Find.named(card, JPanel.class, NowRunningSection.LINES), JLabel.class))
                .extracting(JLabel::getText).containsExactly(
                        "File traversal — tools/call read_file · params.arguments.path", "Session lifecycle — ping");
    }

    @Test
    void theErrorsLinkOpensTheLastErrors() {
        publish(ScanSnapshots.withErrors(1, List.of(new ScanError(START.plusSeconds(5),
                Optional.of("File traversal"), "https://mcp.example/mcp", Optional.of("params.path"),
                "IllegalStateException: boom"))));
        JPanel errors = Find.named(card(1), JPanel.class, ScanErrorsSection.NAME);
        assertThat(errors.isVisible()).isFalse();

        Find.named(card(1), HyperlinkLabel.class, CountsLine.ERRORS_LINK).activate();

        assertThat(errors.isVisible()).isTrue();
        JTable table = Find.all(errors, JTable.class).get(0);
        assertThat(IntStream.range(0, table.getColumnCount()).mapToObj(column -> table.getValueAt(0, column)))
                .containsExactly("10:00:05", "File traversal", "https://mcp.example/mcp", "params.path",
                        "IllegalStateException: boom");
    }

    @Test
    void theIssueListShowsTheScansIssuesAndTheirDetailBesideIt() {
        AuditIssue issue = Issues.issue("OS command injection", AuditIssueSeverity.HIGH);
        publish(ScanSnapshots.withIssues(1, List.of(issue)));
        assertThat(Find.all(card(1), IssueDetailPanel.class)).isEmpty();
        assertThat(editors.created).isZero();

        Find.named(card(1), JTable.class, IssueListPanel.TABLE).setRowSelectionInterval(0, 0);

        assertThat(Find.named(card(1), JEditorPane.class, IssueDetailPanel.ADVISORY).getText())
                .contains("OS command injection");
    }

    @Test
    void oneShowInTargetLinkPerCardOpensTheScansHostOrTheSelectedIssue() {
        AuditIssue issue = Issues.issue("OS command injection", AuditIssueSeverity.HIGH);
        publish(ScanSnapshots.withIssues(1, List.of(issue)));
        assertThat(Find.all(card(1), HyperlinkLabel.class)).filteredOn(link -> "Show in Target".equals(link.getText()))
                .hasSize(1);
        HyperlinkLabel link = Find.named(card(1), HyperlinkLabel.class, CountsLine.SHOW_IN_TARGET);

        link.activate();
        Find.named(card(1), JTable.class, IssueListPanel.TABLE).setRowSelectionInterval(0, 0);
        link.activate();

        assertThat(shownInTarget).containsExactly(TargetLocation.host("http://mcp.example"),
                TargetLocation.of(issue));
    }

    @Test
    void showInTargetFollowsTheTablesSelectionAfterADeselectOrAnEvictedIssue() {
        AuditIssue first = Issues.issue("OS command injection", AuditIssueSeverity.HIGH);
        AuditIssue second = Issues.issue("Path traversal", AuditIssueSeverity.HIGH);
        publish(ScanSnapshots.withIssues(1, List.of(first, second)));
        JTable table = Find.named(card(1), JTable.class, IssueListPanel.TABLE);
        HyperlinkLabel link = Find.named(card(1), HyperlinkLabel.class, CountsLine.SHOW_IN_TARGET);

        table.setRowSelectionInterval(0, 0);
        table.clearSelection();
        link.activate();
        table.setRowSelectionInterval(1, 1);
        publish(ScanSnapshots.withIssues(1, List.of(first)));
        link.activate();

        assertThat(shownInTarget).containsExactly(TargetLocation.host("http://mcp.example"),
                TargetLocation.host("http://mcp.example"));
    }

    @Test
    void theCountsLineColoursEachSeverityLikeTheIssueRows() {
        publish(ScanSnapshots.withFindings(1, ScanSnapshots.findings(4,
                Map.of(AuditIssueSeverity.HIGH, 1, AuditIssueSeverity.INFORMATION, 3))));

        assertThat(label(card(1), CountsLine.ISSUE_COUNT).getText()).isEqualTo("4 issues");
        JLabel high = label(card(1), CountsLine.SEVERITY_PREFIX + "High");
        assertThat(high.getText()).isEqualTo("1 High");
        assertThat(((DotIcon) high.getIcon()).color()).isEqualTo(SeverityColors.of(AuditIssueSeverity.HIGH).get());
        assertThat(label(card(1), CountsLine.SEVERITY_PREFIX + "Info").getText()).isEqualTo("3 Info");
        assertThat(label(card(1), CountsLine.SEVERITY_PREFIX + "Medium").isVisible()).isFalse();
    }

    @Test
    void aFinishedScanSaysHowManyCollaboratorPayloadsAreStillWatchedAndRereadsEverySecond() {
        publish(ScanSnapshots.finishedWatching(1, 3));

        JLabel watched = label(card(1), ScanCardBody.WATCHED);
        assertThat(watched.getText()).isEqualTo("3 Collaborator payloads still watched");
        assertThat(watched.isVisible()).isTrue();
        assertThat(scheduler.pendingCount()).isEqualTo(1);
        assertThat(scheduler.lastDelay()).isEqualTo(ScansPanel.WATCHED_REREAD_MILLIS);

        scans.setSnapshots(ScanSnapshots.finished(1));
        scheduler.runAll();
        assertThat(watched.isVisible()).isFalse();
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void aReadDeliveredAfterDisposeSchedulesNothingMore() {
        scans.setSnapshots(ScanSnapshots.finishedWatching(1, 3));

        panel.dispose();
        scheduler.runNext();

        assertThat(cards()).isEmpty();
        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void theTitleCountsRunningScans() {
        publish(ScanSnapshots.running(2), ScanSnapshots.finished(1));
        assertThat(panel.title()).isEqualTo("Scans (1 running)");

        publish(ScanSnapshots.finished(1));
        assertThat(panel.title()).isEqualTo("Scans");
    }

    @Test
    void disposeStopsListening() {
        panel.dispose();

        assertThat(scans.listeners).isEmpty();
    }

    private void publish(com.mcpscanner.engine.ScanSnapshot... snapshots) {
        scans.publish(snapshots);
        scheduler.runReady();
    }

    private List<ScanCard> cards() {
        return Find.all(panel, ScanCard.class);
    }

    private ScanCard card(long id) {
        return Find.named(panel, ScanCard.class, ScanCard.CARD_PREFIX + id);
    }

    private static boolean expanded(ScanCard card) {
        return Find.named(card, ScanCardBody.class, ScanCardBody.BODY).isVisible();
    }

    private static JLabel label(ScanCard card, String name) {
        return Find.named(card, JLabel.class, name);
    }

    private static JButton button(ScanCard card, String name) {
        return Find.named(card, JButton.class, name);
    }
}
