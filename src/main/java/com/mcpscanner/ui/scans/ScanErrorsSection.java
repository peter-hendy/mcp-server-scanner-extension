package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanError;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.JPanel;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.util.List;

final class ScanErrorsSection extends JPanel {

    static final String NAME = "scan-errors";

    private final ScanErrorTableModel model;

    ScanErrorsSection(ScanText text) {
        super(new BorderLayout());
        this.model = new ScanErrorTableModel(text);
        setName(NAME);
        JTable table = new JTable(model);
        SwingHtmlGuard.guardStringColumns(table);
        add(table.getTableHeader(), BorderLayout.NORTH);
        add(table, BorderLayout.CENTER);
        setVisible(false);
    }

    void show(List<ScanError> recent) {
        model.replace(recent);
    }

    void toggle() {
        setVisible(!isVisible());
        revalidate();
    }
}
