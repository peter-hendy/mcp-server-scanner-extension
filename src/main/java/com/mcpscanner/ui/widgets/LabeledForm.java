package com.mcpscanner.ui.widgets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class LabeledForm extends JPanel {

    private static final Insets CELL_INSETS = new Insets(4, 4, 4, 4);
    private static final int PADDING = 8;
    private static final int LABEL_COLUMN_WIDTH = 160;
    public static final String REQUIRED_MARKER = "*";

    private final int labelColumnWidth;
    private int nextRow;

    public LabeledForm() {
        this(LABEL_COLUMN_WIDTH);
    }

    public LabeledForm(int labelColumnWidth) {
        super(new GridBagLayout());
        this.labelColumnWidth = labelColumnWidth;
        setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
    }

    public LabeledForm withoutTopPadding() {
        setBorder(BorderFactory.createEmptyBorder(0, PADDING, PADDING, PADDING));
        return this;
    }

    public LabeledForm withoutBottomPadding() {
        setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, 0, PADDING));
        return this;
    }

    public Row addRow(String label, JComponent field) {
        return addLabeledRow(label, field, GridBagConstraints.HORIZONTAL);
    }

    public Row addCompactRow(String label, JComponent field) {
        return addLabeledRow(label, field, GridBagConstraints.NONE);
    }

    private Row addLabeledRow(String label, JComponent field, int fill) {
        JLabel labelComponent = new JLabel(label);
        labelComponent.setLabelFor(field);
        JLabel marker = requiredMarker();
        add(labelCell(labelComponent, marker), labelConstraints());
        add(field, fieldConstraints(1, fill));
        return new Row(labelComponent, marker, field);
    }

    private static JLabel requiredMarker() {
        JLabel marker = new JLabel(REQUIRED_MARKER);
        marker.setForeground(ThemeColors.error());
        marker.setToolTipText("Required");
        marker.setVisible(false);
        return marker;
    }

    private JPanel labelCell(JLabel label, JLabel marker) {
        JPanel cell = new JPanel(new BorderLayout(2, 0));
        cell.setOpaque(false);
        cell.add(label, BorderLayout.WEST);
        cell.add(marker, BorderLayout.CENTER);
        Dimension natural = cell.getPreferredSize();
        marker.setVisible(true);
        int widthWithMarker = cell.getPreferredSize().width;
        marker.setVisible(false);
        Dimension columnSize = new Dimension(Math.max(widthWithMarker, labelColumnWidth), natural.height);
        cell.setPreferredSize(columnSize);
        cell.setMinimumSize(columnSize);
        return cell;
    }

    public Row addUnlabeledRow(JComponent field) {
        add(field, fieldConstraints(1, GridBagConstraints.HORIZONTAL));
        return new Row(null, null, field);
    }

    public void keepRowsAtTop() {
        GridBagConstraints filler = baseConstraints(0);
        filler.gridwidth = 2;
        filler.weighty = 1.0;
        filler.insets = new Insets(0, 0, 0, 0);
        add(Box.createGlue(), filler);
        nextRow++;
    }

    public Row addFullWidthRow(JComponent field) {
        add(field, fieldConstraints(0, GridBagConstraints.HORIZONTAL, 2));
        return new Row(null, null, field);
    }

    private GridBagConstraints labelConstraints() {
        GridBagConstraints gbc = baseConstraints(0);
        gbc.anchor = GridBagConstraints.LINE_START;
        return gbc;
    }

    private GridBagConstraints fieldConstraints(int column, int fill) {
        return fieldConstraints(column, fill, 1);
    }

    private GridBagConstraints fieldConstraints(int column, int fill, int width) {
        GridBagConstraints gbc = baseConstraints(column);
        gbc.gridwidth = width;
        gbc.weightx = 1.0;
        gbc.fill = fill;
        gbc.anchor = GridBagConstraints.LINE_START;
        nextRow++;
        return gbc;
    }

    private GridBagConstraints baseConstraints(int column) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = column;
        gbc.gridy = nextRow;
        gbc.insets = CELL_INSETS;
        return gbc;
    }

    public static final class Row {
        private final JLabel label;
        private final JLabel marker;
        private final JComponent field;
        private boolean required;

        private Row(JLabel label, JLabel marker, JComponent field) {
            this.label = label;
            this.marker = marker;
            this.field = field;
        }

        public void setRequired(boolean required) {
            this.required = required;
            if (marker != null) {
                marker.setVisible(required);
            }
        }

        public void setLabel(String text) {
            if (label != null) {
                label.setText(text);
            }
        }

        public void setVisible(boolean visible) {
            if (label != null) {
                label.getParent().setVisible(visible);
                marker.setVisible(visible && required);
            }
            field.setVisible(visible);
        }
    }
}
