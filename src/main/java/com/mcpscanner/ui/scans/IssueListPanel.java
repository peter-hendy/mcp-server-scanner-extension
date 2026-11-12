package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.engine.ScanFindings;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.util.Optional;
import java.util.function.Consumer;

final class IssueListPanel extends JPanel {

    static final String TABLE = "scan-issues-table";

    private final IssueTableModel model = new IssueTableModel();
    private final JTable table = Named.as(TABLE, new JTable(model));
    private final Consumer<AuditIssue> onSelect;
    private boolean replacing;

    IssueListPanel(Consumer<AuditIssue> onSelect) {
        super(new BorderLayout());
        this.onSelect = onSelect;
        SwingHtmlGuard.guardStringColumns(table);
        table.getColumnModel().getColumn(IssueTableModel.Column.SEVERITY.ordinal())
                .setCellRenderer(new SeverityCellRenderer(model));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> selectionChanged(event.getValueIsAdjusting()));
        add(table.getTableHeader(), BorderLayout.NORTH);
        add(table, BorderLayout.CENTER);
    }

    void show(ScanFindings findings) {
        Optional<AuditIssue> selected = selectedIssue();
        replacing = true;
        try {
            if (model.replace(findings)) {
                selected.map(model::rowOf).filter(row -> row >= 0)
                        .ifPresent(row -> table.setRowSelectionInterval(row, row));
            }
        } finally {
            replacing = false;
        }
    }

    Optional<AuditIssue> selectedIssue() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.issueAt(row));
    }

    private void selectionChanged(boolean adjusting) {
        if (!adjusting && !replacing) {
            selectedIssue().ifPresent(onSelect);
        }
    }
}
