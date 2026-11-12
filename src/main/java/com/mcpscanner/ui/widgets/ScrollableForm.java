package com.mcpscanner.ui.widgets;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

public final class ScrollableForm extends JPanel implements Scrollable {

    private static final int UNIT_INCREMENT = 16;

    private ScrollableForm(JComponent content) {
        super(new BorderLayout());
        add(content, BorderLayout.CENTER);
    }

    public static JScrollPane wrap(JComponent content) {
        JScrollPane scrollPane = new JScrollPane(new ScrollableForm(content),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    public static JScrollPane wrapAtTop(JComponent content) {
        JPanel top = new JPanel(new BorderLayout());
        top.add(content, BorderLayout.NORTH);
        return wrap(top);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return UNIT_INCREMENT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(UNIT_INCREMENT, visibleRect.height - UNIT_INCREMENT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
    }
}
