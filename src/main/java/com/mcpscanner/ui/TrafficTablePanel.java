package com.mcpscanner.ui;

import com.mcpscanner.proxy.observe.McpExchange;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.util.List;

public final class TrafficTablePanel extends JPanel {

    private final TrafficTableModel tableModel;
    private final JTable table;

    public TrafficTablePanel() {
        super(new BorderLayout());
        this.tableModel = new TrafficTableModel();
        this.table = new JTable(tableModel);
        this.table.setAutoCreateRowSorter(true);
        this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        SwingHtmlGuard.guardStringColumns(table);

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Must be called on the Swing EDT: delegates to {@link TrafficTableModel#addExchange} which
     * fires a {@code TableModel} event. An off-EDT producer (e.g. {@code McpExchangeLog}) must
     * marshal via {@code SwingUtilities.invokeLater(...)}, as {@code LoggerPanel.onEntry} does.
     */
    public void addExchange(McpExchange exchange) {
        tableModel.addExchange(exchange);
    }

    /**
     * Must be called on the Swing EDT: delegates to {@link TrafficTableModel#setExchanges} which
     * fires a {@code TableModel} event. An off-EDT producer (e.g. {@code McpExchangeLog}) must
     * marshal via {@code SwingUtilities.invokeLater(...)}, as {@code LoggerPanel.onEntry} does.
     */
    public void setExchanges(List<McpExchange> exchanges) {
        tableModel.setExchanges(exchanges);
    }

    TrafficTableModel getTableModel() {
        return tableModel;
    }

    JTable getTableForTest() {
        return table;
    }
}
