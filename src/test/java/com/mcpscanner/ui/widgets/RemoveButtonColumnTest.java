package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.Component;

import static org.assertj.core.api.Assertions.assertThat;

class RemoveButtonColumnTest {

    private static final int REMOVE_COLUMN = 1;

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[][]{{"keep", ""}, {"drop", ""}}, new Object[]{"Name", ""});
    private final JTable table = new JTable(model);

    @Test
    void removableRowsRenderAnAccessibleRemoveButton() {
        install();

        Component cell = renderedCell(1);

        assertThat(cell).isInstanceOf(JButton.class);
        assertThat(((JButton) cell).getToolTipText()).isEqualTo("Remove thing");
        assertThat(cell.getAccessibleContext().getAccessibleName()).isEqualTo("Remove thing");
    }

    @Test
    void fixedRowsRenderNoButton() {
        install();

        assertThat(renderedCell(0)).isNotInstanceOf(JButton.class);
    }

    @Test
    void clickingTheEditorButtonRemovesThatRow() {
        install();

        Component editor = table.getColumnModel().getColumn(REMOVE_COLUMN).getCellEditor()
                .getTableCellEditorComponent(table, "", false, 1, REMOVE_COLUMN);
        ((JButton) editor).doClick();

        assertThat(model.getRowCount()).isEqualTo(1);
        assertThat(model.getValueAt(0, 0)).isEqualTo("keep");
    }

    @Test
    void clickingOnAFixedRowRemovesNothing() {
        install();
        TableCellEditor editor = table.getColumnModel().getColumn(REMOVE_COLUMN).getCellEditor();
        JButton button = (JButton) editor.getTableCellEditorComponent(table, "", false, 1, REMOVE_COLUMN);

        editor.getTableCellEditorComponent(table, "", false, 0, REMOVE_COLUMN);
        button.doClick();

        assertThat(model.getRowCount()).isEqualTo(2);
    }

    @Test
    void columnIsKeptNarrow() {
        install();

        TableColumn column = table.getColumnModel().getColumn(REMOVE_COLUMN);
        assertThat(column.getMinWidth()).isEqualTo(36);
        assertThat(column.getMaxWidth()).isEqualTo(48);
    }

    private void install() {
        new RemoveButtonColumn("Remove thing", row -> row > 0, model::removeRow).installOn(table, REMOVE_COLUMN);
    }

    private Component renderedCell(int row) {
        return table.getColumnModel().getColumn(REMOVE_COLUMN).getCellRenderer()
                .getTableCellRendererComponent(table, "", false, false, row, REMOVE_COLUMN);
    }
}
