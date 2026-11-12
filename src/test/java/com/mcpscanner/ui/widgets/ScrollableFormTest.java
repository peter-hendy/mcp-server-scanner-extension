package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

import static org.assertj.core.api.Assertions.assertThat;

class ScrollableFormTest {

    @Test
    void wrapAtTopKeepsShortContentAtItsOwnHeight() throws Exception {
        JLabel line = new JLabel("One line");
        line.setPreferredSize(new Dimension(100, 20));
        JScrollPane scroll = ScrollableForm.wrapAtTop(line);

        SwingUtilities.invokeAndWait(() -> {
            scroll.setSize(300, 300);
            layOut(scroll);
        });

        assertThat(line.getHeight()).isEqualTo(20);
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layOut(nested);
            }
        }
    }
}
