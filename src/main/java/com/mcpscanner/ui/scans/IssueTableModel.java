package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.engine.ScanFindings;
import com.mcpscanner.ui.widgets.SeverityLabels;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

final class IssueTableModel extends AbstractTableModel {

    enum Column {
        SEVERITY("Severity"),
        ISSUE("Issue"),
        TESTED("Tested");

        private final String header;

        Column(String header) {
            this.header = header;
        }
    }

    private final Map<AuditIssue, String> testedItems = new IdentityHashMap<>();
    private List<AuditIssue> issues = List.of();
    private ScanFindings findings = new ScanFindings(List.of(), 0, Map.of());

    boolean replace(ScanFindings latest) {
        findings = latest;
        List<AuditIssue> ordered = latest.kept().stream().sorted(IssueOrder.MOST_SEVERE_FIRST).toList();
        if (holdsTheSameInstances(ordered)) {
            return false;
        }
        issues = ordered;
        forgetTestedItemsNotIn(ordered);
        fireTableDataChanged();
        return true;
    }

    AuditIssue issueAt(int row) {
        return issues.get(row);
    }

    int rowOf(AuditIssue issue) {
        return IntStream.range(0, issues.size()).filter(row -> issues.get(row) == issue).findFirst().orElse(-1);
    }

    @Override
    public int getRowCount() {
        return issues.size();
    }

    @Override
    public int getColumnCount() {
        return Column.values().length;
    }

    @Override
    public String getColumnName(int column) {
        return Column.values()[column].header;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        AuditIssue issue = issues.get(row);
        return switch (Column.values()[column]) {
            case SEVERITY -> SeverityLabels.of(issue.severity());
            case ISSUE -> issue.name();
            case TESTED -> testedItems.computeIfAbsent(issue, this::testedItem);
        };
    }

    private String testedItem(AuditIssue issue) {
        return TestedItem.of(issue, findings.testedRequest(issue));
    }

    private void forgetTestedItemsNotIn(List<AuditIssue> shown) {
        Set<AuditIssue> current = Collections.newSetFromMap(new IdentityHashMap<>());
        current.addAll(shown);
        testedItems.keySet().retainAll(current);
    }

    private boolean holdsTheSameInstances(List<AuditIssue> latest) {
        return latest.size() == issues.size()
                && IntStream.range(0, latest.size()).allMatch(row -> latest.get(row) == issues.get(row));
    }
}
