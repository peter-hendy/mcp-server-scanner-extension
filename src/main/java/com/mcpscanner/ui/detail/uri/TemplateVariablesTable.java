package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.ui.detail.RowMenu;
import com.mcpscanner.ui.widgets.PopupTrigger;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class TemplateVariablesTable extends AbstractTableModel {

    private static final List<String> COLUMNS = List.of("Variable", "Position", "RFC 6570 expansion");
    private static final List<Function<PlacedVariable, String>> CELLS =
            List.of(PlacedVariable::name, PlacedVariable::position, PlacedVariable::expansion);

    private final List<PlacedVariable> variables;

    private TemplateVariablesTable(List<PlacedVariable> variables) {
        this.variables = List.copyOf(variables);
    }

    static JComponent of(List<PlacedVariable> variables, RowMenu variableMenu) {
        TemplateVariablesTable model = new TemplateVariablesTable(variables);
        JTable table = new JTable(model);
        SwingHtmlGuard.guardStringColumns(table);
        PopupTrigger.install(table, event -> model.menuAt(table, event, variableMenu));
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(table.getTableHeader(), BorderLayout.NORTH);
        holder.add(table, BorderLayout.CENTER);
        return holder;
    }

    private Optional<JPopupMenu> menuAt(JTable table, MouseEvent event, RowMenu variableMenu) {
        int row = table.rowAtPoint(event.getPoint());
        return row < 0 ? Optional.empty() : variableMenu.menuFor(variables.get(row).name());
    }

    @Override
    public int getRowCount() {
        return variables.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.size();
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS.get(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        return CELLS.get(column).apply(variables.get(row));
    }
}
