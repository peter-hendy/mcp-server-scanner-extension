package com.mcpscanner.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;

final class DetailCard {

    private static final int PADDING = 8;

    private DetailCard() {
    }

    static JPanel of(JComponent body, JComponent... headingLines) {
        JPanel heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        for (JComponent line : headingLines) {
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            heading.add(line);
        }
        JPanel card = new JPanel(new BorderLayout(0, PADDING));
        card.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        card.add(heading, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }
}
