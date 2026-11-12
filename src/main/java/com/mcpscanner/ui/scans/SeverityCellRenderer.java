package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.ui.widgets.DotIcon;
import com.mcpscanner.ui.widgets.SeverityColors;
import com.mcpscanner.ui.widgets.SeverityLabels;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;

final class SeverityCellRenderer extends DefaultTableCellRenderer {

    private static final int ICON_GAP = 6;

    private final IssueTableModel model;

    SeverityCellRenderer(IssueTableModel model) {
        this.model = model;
        SwingHtmlGuard.disableHtml(this);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                                                   int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
        AuditIssue issue = model.issueAt(table.convertRowIndexToModel(row));
        label.setIcon(new DotIcon(SeverityColors.of(issue.severity())));
        label.setIconTextGap(ICON_GAP);
        label.setToolTipText(SeverityLabels.of(issue.severity()) + " severity, "
                + SeverityLabels.of(issue.confidence()) + " confidence");
        return label;
    }
}
