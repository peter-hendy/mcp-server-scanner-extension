package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import static org.assertj.core.api.Assertions.assertThat;

class WholeRowScrollPaneTest {

    private static final int ROW_HEIGHT = 20;

    @Test
    void aTableTallerThanTheViewShowsOnlyWholeRows() throws Exception {
        WholeRowScrollPane scroll = laidOut(40, 230);

        assertThat(scroll.getViewport().getHeight() % ROW_HEIGHT).isZero();
        assertThat(scroll.getViewport().getHeight()).isPositive();
    }

    @Test
    void aTableThatFitsKeepsTheFullView() throws Exception {
        WholeRowScrollPane scroll = laidOut(3, 230);

        assertThat(scroll.getViewport().getHeight() % ROW_HEIGHT).isNotZero();
    }

    private static WholeRowScrollPane laidOut(int rows, int height) throws Exception {
        JTable table = new JTable(new DefaultTableModel(rows, 2));
        table.setRowHeight(ROW_HEIGHT);
        WholeRowScrollPane scroll = new WholeRowScrollPane(table);
        scroll.setColumnHeaderView(null);
        SwingUtilities.invokeAndWait(() -> {
            scroll.setSize(300, height);
            scroll.doLayout();
        });
        return scroll;
    }
}
