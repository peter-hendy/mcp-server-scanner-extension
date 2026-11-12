package com.mcpscanner.ui.widgets;

import javax.swing.DefaultCellEditor;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Component;

final class MaskedValueColumn {

    private static final String MASKED = "••••••••";

    private final JToggleButton eye;

    MaskedValueColumn(JToggleButton eye) {
        this.eye = eye;
    }

    void installOn(JTable table, int columnIndex) {
        TableColumn column = table.getColumnModel().getColumn(columnIndex);
        column.setCellRenderer(new Renderer());
        JPasswordField field = new JPasswordField();
        EchoMask echo = new EchoMask(field);
        column.setCellEditor(new Editor(field, echo));
        eye.addItemListener(event -> {
            echo.apply(eye.isSelected());
            table.repaint();
        });
    }

    private String shown(Object value) {
        String text = value == null ? "" : value.toString();
        return eye.isSelected() || text.isEmpty() ? text : MASKED;
    }

    private final class Renderer extends DefaultTableCellRenderer {
        Renderer() {
            SwingHtmlGuard.disableHtml(this);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            return super.getTableCellRendererComponent(table, shown(value), selected, focused, row, column);
        }
    }

    private final class Editor extends DefaultCellEditor {
        private final EchoMask echo;

        Editor(JPasswordField field, EchoMask echo) {
            super(field);
            this.echo = echo;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean selected, int row,
                                                     int column) {
            echo.apply(eye.isSelected());
            return super.getTableCellEditorComponent(table, value, selected, row, column);
        }
    }
}
