package com.mcpscanner.ui;

import com.mcpscanner.proxy.observe.McpExchange;
import com.mcpscanner.proxy.observe.preflight.PreflightReport;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TrafficTablePanel extends JPanel {

    private final TrafficTableModel tableModel;
    private final JTable table;
    private final ProxyControlPanel controlPanel;

    public TrafficTablePanel(boolean proxyInitiallyEnabled, Consumer<Boolean> onProxyToggle) {
        super(new BorderLayout());
        this.tableModel = new TrafficTableModel();
        this.table = new JTable(tableModel);
        this.table.setAutoCreateRowSorter(true);
        this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        SwingHtmlGuard.guardStringColumns(table);
        this.controlPanel = new ProxyControlPanel(proxyInitiallyEnabled, onProxyToggle);

        add(controlPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Supplies the proxy preflight report to the header control so the operator can run it on demand.
     * EDT-only.
     */
    public void attachPreflightSource(Supplier<PreflightReport> source) {
        controlPanel.attachPreflightSource(source);
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

    ProxyControlPanel controlPanelForTest() {
        return controlPanel;
    }
}
