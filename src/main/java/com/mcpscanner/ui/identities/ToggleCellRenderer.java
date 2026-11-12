package com.mcpscanner.ui.identities;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.util.function.IntFunction;

final class ToggleCellRenderer implements TableCellRenderer {

    private final AbstractButton toggle;
    private final IntFunction<String> tooltip;
    private final JLabel blank = new JLabel();

    ToggleCellRenderer(AbstractButton toggle, IntFunction<String> tooltip) {
        this.toggle = toggle;
        this.tooltip = tooltip;
        toggle.setHorizontalAlignment(SwingConstants.CENTER);
        toggle.setBorderPainted(false);
        toggle.setOpaque(true);
        blank.setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                                                   int row, int column) {
        toggle.setSelected(Boolean.TRUE.equals(value));
        toggle.setEnabled(table.isCellEditable(row, column));
        toggle.setToolTipText(tooltip.apply(row));
        Component cell = value == null ? blank : toggle;
        cell.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
        return cell;
    }
}
