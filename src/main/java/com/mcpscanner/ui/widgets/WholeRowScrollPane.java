package com.mcpscanner.ui.widgets;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneLayout;
import java.awt.Container;
import java.awt.Rectangle;

public final class WholeRowScrollPane extends JScrollPane {

    public WholeRowScrollPane(JTable table) {
        super(table);
        setLayout(new WholeRowLayout(table));
    }

    private static final class WholeRowLayout extends ScrollPaneLayout {

        private final JTable table;

        private WholeRowLayout(JTable table) {
            this.table = table;
        }

        @Override
        public void layoutContainer(Container parent) {
            super.layoutContainer(parent);
            if (viewport == null || table.getRowHeight() <= 0) {
                return;
            }
            Rectangle bounds = viewport.getBounds();
            int partialRow = bounds.height % table.getRowHeight();
            if (table.getPreferredSize().height > bounds.height && bounds.height > partialRow) {
                viewport.setBounds(bounds.x, bounds.y, bounds.width, bounds.height - partialRow);
            }
        }
    }
}
