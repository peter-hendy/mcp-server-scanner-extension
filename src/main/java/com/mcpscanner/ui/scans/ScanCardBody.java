package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanSnapshot;
import com.mcpscanner.ui.detail.DetailLabels;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

final class ScanCardBody extends JPanel {

    static final String BODY = "scan-body";
    static final String PROGRESS = "scan-progress";
    static final String WATCHED = "scan-watched";

    private final ScanText text;
    private final JProgressBar progress = Named.as(PROGRESS, new JProgressBar());
    private final JLabel watched = Named.as(WATCHED, DetailLabels.muted(""));
    private final NowRunningSection nowRunning;
    private final ScanErrorsSection errors;
    private final IssuesSection issues;
    private final CountsLine counts;
    private String origin = "";

    ScanCardBody(ScanText text, TargetNavigator navigator, MessageEditors editors) {
        this.text = text;
        this.nowRunning = new NowRunningSection(text);
        this.errors = new ScanErrorsSection(text);
        this.issues = new IssuesSection(editors);
        this.counts = new CountsLine(text, errors::toggle, () -> navigator.show(target()));
        setName(BODY);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(6, 22, 6, 0));
        progress.setStringPainted(true);
        addRow(progress);
        addRow(counts);
        addRow(watched);
        addRow(nowRunning);
        addRow(errors);
        addRow(issues);
    }

    void update(ScanSnapshot snapshot) {
        origin = snapshot.label().origin();
        progress.setMaximum(Math.max(1, snapshot.progress().planned()));
        progress.setValue(snapshot.progress().completed());
        progress.setString(text.progress(snapshot));
        counts.update(snapshot);
        watched.setText(text.watched(snapshot.progress().watchedPayloads()));
        watched.setVisible(snapshot.awaitingCollaborator());
        nowRunning.show(snapshot.nowRunning());
        errors.show(snapshot.errors().recent());
        issues.show(snapshot.issues());
    }

    private TargetLocation target() {
        return issues.selectedIssue().map(TargetLocation::of).orElseGet(() -> TargetLocation.host(origin));
    }

    private void addRow(JComponent row) {
        row.setAlignmentX(LEFT_ALIGNMENT);
        add(row);
        add(Box.createVerticalStrut(4));
    }
}
