package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.engine.ScanFindings;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Optional;

final class IssuesSection extends JPanel {

    private final MessageEditors editors;
    private final IssueListPanel list = new IssueListPanel(this::showDetail);
    private IssueDetailPanel detail;

    IssuesSection(MessageEditors editors) {
        super(new BorderLayout(8, 0));
        this.editors = editors;
        add(list, BorderLayout.CENTER);
    }

    void show(ScanFindings findings) {
        list.show(findings);
    }

    Optional<AuditIssue> selectedIssue() {
        return list.selectedIssue();
    }

    private void showDetail(AuditIssue issue) {
        detail().show(issue);
    }

    private IssueDetailPanel detail() {
        if (detail == null) {
            detail = new IssueDetailPanel(editors);
            add(detail, BorderLayout.EAST);
            revalidate();
        }
        return detail;
    }
}
