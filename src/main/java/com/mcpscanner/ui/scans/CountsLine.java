package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.engine.ScanFindings;
import com.mcpscanner.engine.ScanSnapshot;
import com.mcpscanner.ui.widgets.DotIcon;
import com.mcpscanner.ui.widgets.HyperlinkLabel;
import com.mcpscanner.ui.widgets.SeverityColors;
import com.mcpscanner.ui.widgets.SeverityLabels;
import com.mcpscanner.ui.widgets.SeverityOrder;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;

final class CountsLine extends JPanel {

    static final String REQUESTS = "scan-requests";
    static final String ERRORS_LINK = "scan-errors-link";
    static final String ISSUE_COUNT = "scan-issue-count";
    static final String SHOW_IN_TARGET = "scan-show-in-target";
    static final String SEVERITY_PREFIX = "scan-severity-";

    private final ScanText text;
    private final JLabel requests = Named.as(REQUESTS, new JLabel());
    private final HyperlinkLabel errorsLink = Named.as(ERRORS_LINK, new HyperlinkLabel(""));
    private final JLabel issueCount = Named.as(ISSUE_COUNT, new JLabel());
    private final Map<AuditIssueSeverity, JLabel> severityCounts = new LinkedHashMap<>();
    private final HyperlinkLabel showInTarget = Named.as(SHOW_IN_TARGET, new HyperlinkLabel("Show in Target"));

    CountsLine(ScanText text, Runnable openErrors, Runnable openTarget) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.text = text;
        errorsLink.onActivate(openErrors);
        showInTarget.onActivate(openTarget);
        SeverityOrder.RATED.forEach(severity -> severityCounts.put(severity, severityLabel(severity)));
        add(requests);
        add(separator());
        add(errorsLink);
        add(separator());
        add(issueCount);
        severityCounts.values().forEach(this::add);
        add(separator());
        add(showInTarget);
    }

    void update(ScanSnapshot snapshot) {
        requests.setText(text.requests(snapshot.requests()));
        errorsLink.setText(text.errors(snapshot.errors().total()));
        errorsLink.setEnabled(snapshot.errors().total() > 0);
        issueCount.setText(text.issueTotal(snapshot.issues()));
        severityCounts.forEach((severity, label) -> showSeverityCount(label, severity, snapshot.issues()));
    }

    private void showSeverityCount(JLabel label, AuditIssueSeverity severity, ScanFindings findings) {
        int count = findings.count(severity);
        label.setText(text.severityCount(count, severity));
        label.setVisible(count > 0);
    }

    private static JLabel severityLabel(AuditIssueSeverity severity) {
        JLabel label = Named.as(SEVERITY_PREFIX + SeverityLabels.of(severity), new JLabel());
        label.setIcon(new DotIcon(SeverityColors.of(severity)));
        label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        return label;
    }

    private static JLabel separator() {
        return new JLabel(ScanText.SEPARATOR);
    }
}
