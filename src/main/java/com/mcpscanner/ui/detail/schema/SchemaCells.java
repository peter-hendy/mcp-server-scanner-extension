package com.mcpscanner.ui.detail.schema;

import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.widgets.WrapLayout;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;

final class SchemaCells {

    private static final String BRANCH = "└ ";
    private static final String EXPANDED = "▾ ";
    private static final int GAP = 4;

    private SchemaCells() {
    }

    static JComponent name(SchemaRow row) {
        JPanel cell = flow();
        String marker = (row.depth() > 0 ? BRANCH : "") + (row.hasChildren() ? EXPANDED : "");
        if (!marker.isEmpty()) {
            cell.add(SchemaLabels.muted(marker));
        }
        cell.add(row.depth() == 0 ? SchemaLabels.strongCode(row.name()) : SchemaLabels.code(row.name()));
        if (row.required()) {
            cell.add(Chip.attention("required"));
        }
        if (row.mcpHeader() != null) {
            cell.add(Chip.neutral("header: " + row.mcpHeader()));
        }
        return cell;
    }

    static JComponent type(SchemaRow row) {
        JPanel cell = flow();
        cell.add(SchemaLabels.code(row.type()));
        if (row.format() != null) {
            cell.add(Chip.neutral("format: " + row.format()));
        }
        return cell;
    }

    static JComponent constraints(SchemaRow row) {
        JPanel cell = stack();
        cell.add(leftAligned(inlineValues(row)));
        row.constraints().stream().filter(SchemaConstraint::block).forEach(block -> cell.add(leftAligned(block(block))));
        return cell;
    }

    static JComponent description(SchemaRow row) {
        return row.description() == null ? SchemaLabels.placeholder("None given") : SchemaLabels.prose(row.description());
    }

    private static JPanel inlineValues(SchemaRow row) {
        JPanel values = flow();
        if (row.refSource() != null) {
            labelled(values, "from", row.refSource());
        }
        enumValues(values, row.enumValues());
        if (row.defaultValue() != null) {
            labelled(values, "default", row.defaultValue());
        }
        row.constraints().stream().filter(constraint -> !constraint.block()).forEach(constraint -> constraint(values, constraint));
        return values;
    }

    private static void enumValues(JPanel values, List<String> enumValues) {
        if (enumValues.isEmpty()) {
            return;
        }
        values.add(SchemaLabels.muted("one of"));
        enumValues.forEach(value -> values.add(Chip.neutral(value)));
    }

    private static void constraint(JPanel values, SchemaConstraint constraint) {
        if (constraint.value() == null) {
            values.add(SchemaLabels.muted(constraint.label()));
        } else {
            labelled(values, constraint.label(), constraint.value());
        }
    }

    private static void labelled(JPanel values, String label, String value) {
        values.add(SchemaLabels.muted(label));
        values.add(SchemaLabels.code(value));
    }

    private static JComponent block(SchemaConstraint constraint) {
        JPanel block = new JPanel(new BorderLayout(0, GAP));
        block.setOpaque(false);
        block.add(SchemaLabels.muted(constraint.label()), BorderLayout.NORTH);
        block.add(SchemaLabels.codeBox(constraint.value()), BorderLayout.CENTER);
        return block;
    }

    private static JPanel flow() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, GAP, 0));
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel stack() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private static JComponent leftAligned(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }
}
