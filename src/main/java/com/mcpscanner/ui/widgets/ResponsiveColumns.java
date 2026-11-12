package com.mcpscanner.ui.widgets;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.Arrays;

public final class ResponsiveColumns extends JPanel {

    private final int minColumnWidth;
    private final int maxColumns;
    private final RowLayout rows;

    public ResponsiveColumns(int minColumnWidth, int maxColumns, int gap) {
        this.minColumnWidth = minColumnWidth;
        this.maxColumns = maxColumns;
        this.rows = new RowLayout(gap);
        setLayout(rows);
        setOpaque(false);
    }

    public int columns() {
        return rows.columns;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        int columns = columnsFor(width);
        if (columns != rows.columns) {
            rows.columns = columns;
            SwingUtilities.invokeLater(this::revalidate);
        }
    }

    private int columnsFor(int width) {
        int fitting = (width + rows.gap) / (minColumnWidth + rows.gap);
        return Math.max(1, Math.min(maxColumns, fitting));
    }

    private static final class RowLayout implements LayoutManager {

        private final int gap;
        private int columns = 1;

        private RowLayout(int gap) {
            this.gap = gap;
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int cellWidth = cellWidth(parent.getWidth() - insets.left - insets.right);
            int[] heights = rowHeights(parent);
            Component[] cells = parent.getComponents();
            int y = insets.top;
            for (int row = 0; row < heights.length; row++) {
                for (int column = 0; column < columns && row * columns + column < cells.length; column++) {
                    int x = insets.left + column * (cellWidth + gap);
                    cells[row * columns + column].setBounds(x, y, cellWidth, heights[row]);
                }
                y += heights[row] + gap;
            }
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int widest = Arrays.stream(parent.getComponents())
                    .mapToInt(cell -> cell.getPreferredSize().width).max().orElse(0);
            int[] heights = rowHeights(parent);
            int height = Arrays.stream(heights).sum() + Math.max(0, heights.length - 1) * gap;
            return new Dimension(columns * widest + (columns - 1) * gap + insets.left + insets.right,
                    height + insets.top + insets.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        @Override
        public void addLayoutComponent(String name, Component component) {
        }

        @Override
        public void removeLayoutComponent(Component component) {
        }

        private int cellWidth(int availableWidth) {
            return Math.max(0, (availableWidth - (columns - 1) * gap) / columns);
        }

        private int[] rowHeights(Container parent) {
            Component[] cells = parent.getComponents();
            int[] heights = new int[(cells.length + columns - 1) / columns];
            for (int index = 0; index < cells.length; index++) {
                heights[index / columns] = Math.max(heights[index / columns], cells[index].getPreferredSize().height);
            }
            return heights;
        }
    }
}
