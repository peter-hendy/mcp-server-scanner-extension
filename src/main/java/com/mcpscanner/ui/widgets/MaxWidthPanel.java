package com.mcpscanner.ui.widgets;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

public final class MaxWidthPanel extends JPanel {

    public MaxWidthPanel(JComponent content, int maxWidth) {
        super(new CappedWidthLayout(maxWidth));
        setOpaque(false);
        add(content);
    }

    private record CappedWidthLayout(int maxWidth) implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component component) {
        }

        @Override
        public void removeLayoutComponent(Component component) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return sized(parent, child(parent).getPreferredSize());
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return sized(parent, child(parent).getMinimumSize());
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int available = parent.getWidth() - insets.left - insets.right;
            child(parent).setBounds(insets.left, insets.top, Math.min(available, maxWidth),
                    parent.getHeight() - insets.top - insets.bottom);
        }

        private Dimension sized(Container parent, Dimension content) {
            Insets insets = parent.getInsets();
            return new Dimension(Math.min(content.width, maxWidth) + insets.left + insets.right,
                    content.height + insets.top + insets.bottom);
        }

        private static Component child(Container parent) {
            return parent.getComponent(0);
        }
    }
}
