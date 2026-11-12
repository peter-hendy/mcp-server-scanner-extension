package com.mcpscanner.ui.widgets;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Component;
import java.awt.Insets;
import java.util.EventObject;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

public final class RemoveButtonColumn {

    private static final String REMOVE_GLYPH = "×";
    private static final int MIN_WIDTH = 36;
    private static final int MAX_WIDTH = 48;
    private static final Insets BUTTON_MARGIN = new Insets(0, 6, 0, 6);

    private final String label;
    private final IntPredicate removable;
    private final IntConsumer remove;

    public RemoveButtonColumn(String label, IntPredicate removable, IntConsumer remove) {
        this.label = label;
        this.removable = removable;
        this.remove = remove;
    }

    public void installOn(JTable table, int columnIndex) {
        TableColumn column = table.getColumnModel().getColumn(columnIndex);
        column.setCellRenderer(new Renderer());
        column.setCellEditor(new Editor());
        column.setMinWidth(MIN_WIDTH);
        column.setMaxWidth(MAX_WIDTH);
    }

    private JButton createButton() {
        JButton button = new JButton(REMOVE_GLYPH);
        button.setMargin(BUTTON_MARGIN);
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.setBorderPainted(true);
        button.setContentAreaFilled(true);
        return button;
    }

    private Component cellFor(JTable table, int row, JButton button, JLabel empty) {
        if (!removable.test(row)) {
            return empty;
        }
        button.setFont(table.getFont());
        return button;
    }

    private final class Renderer implements TableCellRenderer {

        private final JButton button = createButton();
        private final JLabel empty = new JLabel("");

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            return cellFor(table, row, button, empty);
        }
    }

    private final class Editor extends AbstractCellEditor implements TableCellEditor {

        private final JButton button = createButton();
        private final JLabel empty = new JLabel("");
        private JTable table;
        private int currentRow = -1;

        Editor() {
            button.addActionListener(event -> removeCurrentRow());
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }

        @Override
        public boolean isCellEditable(EventObject event) {
            return true;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                     int row, int column) {
            this.table = table;
            this.currentRow = row;
            return cellFor(table, row, button, empty);
        }

        private void removeCurrentRow() {
            if (currentRow >= 0 && currentRow < table.getModel().getRowCount() && removable.test(currentRow)) {
                remove.accept(currentRow);
            }
            fireEditingStopped();
        }
    }
}
