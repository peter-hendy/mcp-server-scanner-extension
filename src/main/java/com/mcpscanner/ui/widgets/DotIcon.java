package com.mcpscanner.ui.widgets;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Supplier;

public final class DotIcon implements Icon {

    private static final int SIZE = 8;

    private Supplier<Color> color;

    public DotIcon(Supplier<Color> color) {
        this.color = color;
    }

    public void setColor(Supplier<Color> color) {
        this.color = color;
    }

    public Color color() {
        return color.get();
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color());
            g2.fillOval(x, y, SIZE, SIZE);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}
