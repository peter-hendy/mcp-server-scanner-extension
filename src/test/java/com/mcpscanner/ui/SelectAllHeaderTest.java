package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelectAllHeaderTest {

    private static final Color DASH = new Color(255, 0, 255);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void partialSelectionPaintsAVisibleDashOverTheCheckbox() {
        ResourceTablePanel panel = new ResourceTablePanel();
        panel.populate(List.of(new McpResourceDefinition("docs://a", "a", "", "x"),
                new McpResourceDefinition("docs://b", "b", "", "x")));
        panel.setAvailability(InventoryView.Availability.CONNECTED);
        JTable table = panel.table();
        table.getModel().setValueAt(false, 0, SelectableInventoryTableModel.SELECT_COLUMN_INDEX);
        table.getTableHeader().setForeground(DASH);

        assertThat(panel.view().selectAllHeader().state()).isEqualTo(SelectAllHeader.State.PARTIAL);
        assertThat(countDashPixels(table)).isGreaterThan(10);
    }

    @Test
    void untickableMalformedRowsDoNotKeepTheHeaderPartial() {
        ResourceTablePanel panel = new ResourceTablePanel();
        panel.populate(List.of(new McpResourceDefinition("docs://a", "a", "", "x"),
                new McpResourceDefinition(null, "broken", "", "x")));
        panel.setAvailability(InventoryView.Availability.CONNECTED);

        assertThat(panel.view().selectAllHeader().state()).isEqualTo(SelectAllHeader.State.ALL);
        panel.view().selectAllHeader().toggle();
        assertThat(panel.view().selectAllHeader().state()).isEqualTo(SelectAllHeader.State.NONE);
    }

    private static int countDashPixels(JTable table) {
        Component cell = table.getColumnModel().getColumn(SelectableInventoryTableModel.SELECT_COLUMN_INDEX)
                .getHeaderRenderer().getTableCellRendererComponent(table, null, false, false, -1, 0);
        cell.setSize(56, 24);
        cell.doLayout();
        BufferedImage image = new BufferedImage(56, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        cell.paint(graphics);
        graphics.dispose();
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getRGB(x, y) == DASH.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }
}
