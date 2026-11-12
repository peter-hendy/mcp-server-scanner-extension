package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JTable;
import java.awt.Component;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceTemplateTablePanelTest {

    private final ResourceTemplateTablePanel panel = new ResourceTemplateTablePanel();

    @Test
    void populateSetsRowCount() {
        panel.populate(createTemplates(3));

        assertThat(panel.getTableModel().getRowCount()).isEqualTo(3);
    }

    @Test
    void populateWithEmptyListClearsRows() {
        panel.populate(createTemplates(4));
        panel.populate(List.of());

        assertThat(panel.getTableModel().getRowCount()).isZero();
    }

    @Test
    void populateDefaultsAllRowsToSelected() {
        List<McpResourceTemplateDefinition> templates = createTemplates(3);

        panel.populate(templates);

        assertThat(panel.selectedResourceTemplates()).isEqualTo(templates);
    }

    @Test
    void deselectingRowExcludesItFromSelectedTemplates() {
        List<McpResourceTemplateDefinition> templates = createTemplates(3);
        panel.populate(templates);

        panel.getTableModel().setValueAt(false, 1, 0);

        assertThat(panel.selectedResourceTemplates())
                .containsExactly(templates.get(0), templates.get(2));
    }

    @Test
    void selectingRowPopulatesDetailPanel() {
        McpResourceTemplateDefinition template = new McpResourceTemplateDefinition(
                "file:///{path}", "files", "filesystem entries", "text/plain");
        panel.populate(List.of(template));

        panel.table().setRowSelectionInterval(0, 0);

        ResourceTemplateDetailPanel detail = panel.detailPanel();
        assertThat(detail.headingLabel().getText()).isEqualTo("files");
        assertThat(OverviewProbe.texts(detail.overview())).contains("file:///{path}", "text/plain");
        assertThat(detail.description().area().getText()).isEqualTo("filesystem entries");
    }

    @Test
    void detailLabelsDisableHtmlRendering() {
        panel.populate(List.of(new McpResourceTemplateDefinition(
                "<html>u", "<html>n", "d", "<html>m")));

        panel.table().setRowSelectionInterval(0, 0);

        ResourceTemplateDetailPanel detail = panel.detailPanel();
        assertThat(detail.headingLabel().getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(OverviewProbe.labelsWithHtmlEnabled(detail.overview())).isEmpty();
        assertThat(OverviewProbe.labelsWithHtmlEnabled(detail.extraHeaderLine())).isEmpty();
    }

    @Test
    void nameColumnRendererDisablesHtmlRendering() {
        panel.populate(List.of(new McpResourceTemplateDefinition(
                "tmpl://x", "<html><img src=http://x>", "d", "text/plain")));

        JTable table = panel.table();
        int column = SelectableInventoryTableModel.SELECT_COLUMN_INDEX + 1;
        Component component = table.getCellRenderer(0, column).getTableCellRendererComponent(
                table, table.getValueAt(0, column), false, false, 0, column);

        assertThat(((JComponent) component).getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
    }

    private List<McpResourceTemplateDefinition> createTemplates(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new McpResourceTemplateDefinition(
                        "tmpl://" + i, "name " + i, "desc " + i, "text/plain"))
                .toList();
    }
}
