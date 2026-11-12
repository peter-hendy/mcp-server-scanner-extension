package com.mcpscanner.ui.widgets;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

final class EyeIcon implements Icon {

    private static final int SIZE = 16;

    private final boolean slashed;

    private EyeIcon(boolean slashed) {
        this.slashed = slashed;
    }

    static EyeIcon open() {
        return new EyeIcon(false);
    }

    static EyeIcon slashed() {
        return new EyeIcon(true);
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            g.setColor(component.getForeground());
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(almond());
            g.fillOval(5, 5, 6, 6);
            if (slashed) {
                g.drawLine(2, 14, 14, 2);
            }
        } finally {
            g.dispose();
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

    private static Path2D almond() {
        Path2D eye = new Path2D.Float();
        eye.moveTo(1, 8);
        eye.quadTo(8, -1, 15, 8);
        eye.quadTo(8, 17, 1, 8);
        eye.closePath();
        return eye;
    }
}
