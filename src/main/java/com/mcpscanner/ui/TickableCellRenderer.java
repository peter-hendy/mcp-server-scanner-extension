package com.mcpscanner.ui;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;

final class TickableCellRenderer implements TableCellRenderer {

    private final TableCellRenderer delegate;

    TickableCellRenderer(TableCellRenderer delegate) {
        this.delegate = delegate;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        Component component = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        component.setEnabled(isTickable(table, row, column));
        return component;
    }

    static boolean isTickable(JTable table, int viewRow, int viewColumn) {
        return table.getModel().isCellEditable(table.convertRowIndexToModel(viewRow),
                table.convertColumnIndexToModel(viewColumn));
    }
}
