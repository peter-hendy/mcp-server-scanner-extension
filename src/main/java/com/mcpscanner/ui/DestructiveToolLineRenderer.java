package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingConstants;
import java.awt.Component;

final class DestructiveToolLineRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, false, false);
        SwingHtmlGuard.disableHtml(label);
        if (value instanceof McpToolDefinition tool) {
            label.setText(DestructiveScanPolicy.formatToolLine(tool));
        }
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return label;
    }
}
