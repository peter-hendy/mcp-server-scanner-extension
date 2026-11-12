package com.mcpscanner.ui.widgets;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

public final class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return wrappedSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = wrappedSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension wrappedSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int available = availableWidth(target) - insets.left - insets.right - getHgap() * 2;
            Dimension total = rowsSize(target, available, preferred);
            total.width += insets.left + insets.right + getHgap() * 2;
            total.height += insets.top + insets.bottom + getVgap() * 2;
            return total;
        }
    }

    private Dimension rowsSize(Container target, int available, boolean preferred) {
        Dimension total = new Dimension(0, 0);
        int rowWidth = 0;
        int rowHeight = 0;
        for (Component component : target.getComponents()) {
            if (!component.isVisible()) {
                continue;
            }
            Dimension size = preferred ? component.getPreferredSize() : component.getMinimumSize();
            if (rowWidth + size.width > available && rowWidth > 0) {
                addRow(total, rowWidth, rowHeight);
                rowWidth = 0;
                rowHeight = 0;
            }
            rowWidth += (rowWidth == 0 ? 0 : getHgap()) + size.width;
            rowHeight = Math.max(rowHeight, size.height);
        }
        addRow(total, rowWidth, rowHeight);
        return total;
    }

    private static int availableWidth(Container target) {
        Container sizing = target;
        while (sizing.getSize().width == 0 && sizing.getParent() != null) {
            sizing = sizing.getParent();
        }
        int width = sizing.getSize().width;
        return width == 0 ? Integer.MAX_VALUE : width;
    }

    private void addRow(Dimension total, int rowWidth, int rowHeight) {
        total.width = Math.max(total.width, rowWidth);
        if (total.height > 0) {
            total.height += getVgap();
        }
        total.height += rowHeight;
    }
}
