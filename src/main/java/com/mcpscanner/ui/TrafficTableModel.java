package com.mcpscanner.ui;

import com.mcpscanner.proxy.observe.Direction;
import com.mcpscanner.proxy.observe.McpExchange;

import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TrafficTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {"Time", "Direction", "Method", "Id", "Status"};
    private static final int COLUMN_TIME = 0;
    private static final int COLUMN_DIRECTION = 1;
    private static final int COLUMN_METHOD = 2;
    private static final int COLUMN_ID = 3;
    private static final int COLUMN_STATUS = 4;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String CLIENT_TO_SERVER = "C→S";
    private static final String SERVER_TO_CLIENT = "S→C";

    private final List<McpExchange> exchanges = new ArrayList<>();

    /**
     * Must be called on the Swing EDT: mutates the row list and fires a {@code TableModel} event.
     * An off-EDT producer (e.g. {@code McpExchangeLog}) must marshal via
     * {@code SwingUtilities.invokeLater(...)}, as {@code LoggerPanel.onEntry} does.
     */
    public void addExchange(McpExchange exchange) {
        exchanges.add(exchange);
        int row = exchanges.size() - 1;
        fireTableRowsInserted(row, row);
    }

    /**
     * Must be called on the Swing EDT: mutates the row list and fires a {@code TableModel} event.
     * An off-EDT producer (e.g. {@code McpExchangeLog}) must marshal via
     * {@code SwingUtilities.invokeLater(...)}, as {@code LoggerPanel.onEntry} does.
     */
    public void setExchanges(List<McpExchange> replacements) {
        exchanges.clear();
        exchanges.addAll(replacements);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return exchanges.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public Object getValueAt(int row, int column) {
        McpExchange exchange = exchanges.get(row);
        return switch (column) {
            case COLUMN_TIME -> formatTime(exchange);
            case COLUMN_DIRECTION -> directionSymbol(exchange.direction());
            case COLUMN_METHOD -> orEmpty(exchange.method());
            case COLUMN_ID -> orEmpty(exchange.jsonrpcId());
            case COLUMN_STATUS -> exchange.status() != null ? exchange.status().toString() : "";
            default -> "";
        };
    }

    private static String formatTime(McpExchange exchange) {
        if (exchange.timing() == null) {
            return "";
        }
        return TIME_FORMAT.format(exchange.timing().atZone(ZoneId.systemDefault()));
    }

    private static String directionSymbol(Direction direction) {
        return direction == Direction.SERVER_TO_CLIENT ? SERVER_TO_CLIENT : CLIENT_TO_SERVER;
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
