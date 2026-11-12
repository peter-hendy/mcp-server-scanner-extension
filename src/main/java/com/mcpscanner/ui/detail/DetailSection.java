package com.mcpscanner.ui.detail;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.Locale;

public final class DetailSection extends JPanel {

    private static final int HEADING_GAP = 6;
    private static final float HEADING_SIZE_REDUCTION = 2f;

    private final JLabel heading;
    private JComponent body;

    public DetailSection(String title) {
        super(new BorderLayout(0, HEADING_GAP));
        heading = headingFor(title);
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        add(heading, BorderLayout.NORTH);
        setVisible(false);
    }

    public static DetailSection of(String title, JComponent body) {
        DetailSection section = new DetailSection(title);
        section.show(body);
        return section;
    }

    public void show(JComponent content) {
        if (body != null) {
            remove(body);
        }
        body = content;
        if (content != null) {
            add(content, BorderLayout.CENTER);
        }
        setVisible(hasContent());
        revalidate();
        repaint();
    }

    public boolean hasContent() {
        return body != null && body.isVisible();
    }

    public JLabel heading() {
        return heading;
    }

    private static JLabel headingFor(String title) {
        JLabel label = DetailLabels.muted(title.toUpperCase(Locale.ROOT));
        Font font = label.getFont();
        if (font != null) {
            label.setFont(font.deriveFont(Font.BOLD, font.getSize2D() - HEADING_SIZE_REDUCTION));
        }
        return label;
    }
}
