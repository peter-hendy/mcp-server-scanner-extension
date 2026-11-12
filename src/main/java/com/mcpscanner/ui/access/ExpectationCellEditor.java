package com.mcpscanner.ui.access;

import com.mcpscanner.access.AccessRow;
import com.mcpscanner.checks.api.AccessExpectation;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JTable;
import java.awt.Component;

final class ExpectationCellEditor extends DefaultCellEditor {

    private final JComboBox<AccessExpectation> choices;

    ExpectationCellEditor() {
        this(new JComboBox<>(AccessExpectation.values()));
    }

    private ExpectationCellEditor(JComboBox<AccessExpectation> choices) {
        super(choices);
        this.choices = choices;
        choices.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                Object label = value instanceof AccessExpectation expectation ? expectation.label() : value;
                return super.getListCellRendererComponent(list, label, index, selected, focused);
            }
        });
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean selected, int row, int column) {
        super.getTableCellEditorComponent(table, value, selected, row, column);
        choices.setSelectedItem(((AccessRow) value).expectation());
        return choices;
    }

    @Override
    public Object getCellEditorValue() {
        return choices.getSelectedItem();
    }
}
