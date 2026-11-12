package com.mcpscanner.ui.detail;

import com.mcpscanner.ui.widgets.UiStyle;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;

public final class FieldGrid extends JPanel {

    private static final int ROW_GAP = 6;
    private static final int COLUMN_GAP = 16;

    private final boolean monospacedLabels;
    private int rows;

    public FieldGrid() {
        this(false);
    }

    private FieldGrid(boolean monospacedLabels) {
        super(new GridBagLayout());
        this.monospacedLabels = monospacedLabels;
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setVisible(false);
    }

    public static FieldGrid withMonospacedLabels() {
        return new FieldGrid(true);
    }

    public FieldGrid addText(String label, String value) {
        return isBlank(value) ? this : addRow(label, new WrappingText(value));
    }

    public FieldGrid addMonospaced(String label, String value) {
        return isBlank(value) ? this : addRow(label, UiStyle.monospaced(new WrappingText(value)));
    }

    public FieldGrid addComponent(String label, JComponent value) {
        return value == null ? this : addRow(label, value);
    }

    public boolean isEmpty() {
        return rows == 0;
    }

    public int rowCount() {
        return rows;
    }

    private FieldGrid addRow(String label, JComponent value) {
        add(labelFor(label), constraints(0, 0, GridBagConstraints.NONE));
        add(value, constraints(1, 1, GridBagConstraints.HORIZONTAL));
        rows++;
        setVisible(true);
        return this;
    }

    private JLabel labelFor(String text) {
        JLabel label = DetailLabels.muted(Objects.toString(text, ""));
        return monospacedLabels ? UiStyle.monospaced(label) : label;
    }

    private GridBagConstraints constraints(int column, double weight, int fill) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = rows;
        constraints.weightx = weight;
        constraints.fill = fill;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.insets = new Insets(0, 0, ROW_GAP, column == 0 ? COLUMN_GAP : 0);
        return constraints;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
