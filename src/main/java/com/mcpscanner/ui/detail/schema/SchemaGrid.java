package com.mcpscanner.ui.detail.schema;

import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.function.Function;

final class SchemaGrid extends JPanel {

    private static final List<String> HEADINGS = List.of("Parameter", "Type", "Constraints", "Description");
    private static final List<Function<SchemaRow, JComponent>> CELLS = List.of(
            SchemaCells::name, SchemaCells::type, SchemaCells::constraints, SchemaCells::description);
    private static final double[] COLUMN_WEIGHTS = {0.25, 0.2, 0.35, 0.2};
    private static final int CELL_PADDING = 8;
    private static final int ROW_PADDING = 6;
    private static final int INDENT = 16;

    SchemaGrid(List<SchemaRow> rows) {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(BorderFactory.createLineBorder(ThemeColors.separator()));
        for (int column = 0; column < HEADINGS.size(); column++) {
            place(SchemaLabels.strong(HEADINGS.get(column)), column, 0, 0);
        }
        for (int index = 0; index < rows.size(); index++) {
            addRow(rows.get(index), index + 1);
        }
    }

    private void addRow(SchemaRow row, int gridRow) {
        for (int column = 0; column < CELLS.size(); column++) {
            int indent = column == 0 ? row.depth() * INDENT : 0;
            place(CELLS.get(column).apply(row), column, gridRow, indent);
        }
    }

    private void place(JComponent cell, int column, int gridRow, int indent) {
        cell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeColors.separator()),
                BorderFactory.createEmptyBorder(ROW_PADDING, CELL_PADDING + indent, ROW_PADDING, CELL_PADDING)));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = gridRow;
        constraints.weightx = COLUMN_WEIGHTS[column];
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        add(cell, constraints);
    }
}
