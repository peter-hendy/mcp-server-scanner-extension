package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryViewTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void emptyTableWhileDisconnectedExplainsHowToGetItems() {
        ResourceTablePanel panel = new ResourceTablePanel();

        panel.setAvailability(InventoryView.Availability.DISCONNECTED);

        assertThat(panel.view().emptyMessage()).isEqualTo("Connect to list the server's resources.");
        assertThat(panel.view().showingPlaceholder()).isTrue();
    }

    @Test
    void emptyStateMessagesUseTheMutedUiFont() {
        ResourceTablePanel panel = new ResourceTablePanel();

        javax.swing.JLabel message = com.mcpscanner.testutil.SwingTree.descendants(panel.view()).stream()
                .filter(c -> c instanceof javax.swing.JLabel l && l.getText().startsWith("Connect to list"))
                .map(javax.swing.JLabel.class::cast).findFirst().orElseThrow();

        assertThat(message.getForeground()).isEqualTo(com.mcpscanner.ui.widgets.ThemeColors.muted());
        assertThat(message.getFont().getFamily()).isNotEqualTo(java.awt.Font.MONOSPACED);
    }

    @Test
    void emptyTableWhileConnectedSaysTheServerExposesNone() {
        ResourceTablePanel panel = new ResourceTablePanel();

        panel.setAvailability(InventoryView.Availability.CONNECTED);

        assertThat(panel.view().emptyMessage()).isEqualTo("This server exposes no resources.");
    }

    @Test
    void emptyTableAfterAFailedConnectPointsAtTheFailure() {
        ResourceTablePanel panel = new ResourceTablePanel();

        panel.setAvailability(InventoryView.Availability.CONNECTION_FAILED);

        assertThat(panel.view().emptyMessage())
                .isEqualTo("The last connection attempt failed. The status line and the Log panel on the right say why.");
    }

    @Test
    void emptyTableWhileConnectingSaysTheInventoryIsOnItsWay() {
        ResourceTablePanel panel = new ResourceTablePanel();

        panel.setAvailability(InventoryView.Availability.CONNECTING);

        assertThat(panel.view().emptyMessage()).isEqualTo("Connecting… resources appear here once connected.");
    }

    @Test
    void populatedTableShowsTheTable() {
        ResourceTablePanel panel = connectedWith(resource("docs://a", "alpha"), resource("docs://b", "beta"));

        assertThat(panel.view().showingPlaceholder()).isFalse();
    }

    @Test
    void filterHidesRowsThatDoNotMatchAnyColumn() {
        ResourceTablePanel panel = connectedWith(resource("docs://a", "alpha"), resource("docs://b", "beta"));

        panel.view().filterField().setText("BET");

        assertThat(panel.table().getRowCount()).isEqualTo(1);
        assertThat(panel.table().getValueAt(0, 2)).isEqualTo("beta");
    }

    @Test
    void filterWithNoMatchesSaysSo() {
        ResourceTablePanel panel = connectedWith(resource("docs://a", "alpha"));

        panel.view().filterField().setText("zzz");

        assertThat(panel.view().showingPlaceholder()).isTrue();
        assertThat(panel.view().emptyMessage())
                .isEqualTo("No resources match “zzz”. Ticked resources outside the filter are still scanned.");
    }

    @Test
    void selectAllHeaderTicksOnlyTheVisibleRows() {
        ResourceTablePanel panel = connectedWith(resource("docs://a", "alpha"), resource("docs://b", "beta"));
        untickAll(panel);
        panel.view().filterField().setText("alpha");

        panel.view().selectAllHeader().toggle();

        assertThat(panel.selectedResources()).extracting(McpResourceDefinition::name).containsExactly("alpha");
        assertThat(panel.view().selectAllHeader().state()).isEqualTo(SelectAllHeader.State.ALL);
    }

    @Test
    void everyInventoryTableUsesAKeyboardReachableSelectAllHeaderInsteadOfAllNoneButtons() {
        List<TableUnderTest> panels = List.of(
                new TableUnderTest(new ToolTablePanel(), "tools"),
                new TableUnderTest(new ResourceTablePanel(), "resources"),
                new TableUnderTest(new ResourceTemplateTablePanel(), "templates"),
                new TableUnderTest(new PromptTablePanel(), "prompts"));

        assertThat(panels).allSatisfy(entry -> {
            javax.swing.table.JTableHeader header = entry.panel().table().getTableHeader();
            assertThat(header.isFocusable()).isTrue();
            assertThat(header.getAccessibleContext().getAccessibleName()).isEqualTo("Tick all " + entry.plural() + " for scanning");
            assertThat(com.mcpscanner.testutil.SwingTree.descendants(entry.panel().view()))
                    .noneMatch(c -> c instanceof javax.swing.JButton b
                            && List.of("All", "None", "Invert").contains(b.getText()));
        });
    }

    @Test
    void selectionControlsAreDisabledWhileDisconnected() {
        ResourceTablePanel panel = new ResourceTablePanel();
        panel.populate(List.of(resource("docs://a", "alpha")));
        untickAll(panel);

        panel.setAvailability(InventoryView.Availability.DISCONNECTED);
        panel.view().selectAllHeader().toggle();

        assertThat(panel.selectedResources()).isEmpty();
        assertThat(panel.view().filterField().isEnabled()).isFalse();
    }

    @Test
    void noRowSelectedHidesTheDetailSoTheTableTakesTheSpace() {
        ResourceTablePanel panel = connectedWith(resource("docs://a", "alpha"));

        assertThat(panel.view().showingDetail()).isFalse();
        assertThat(visibleTexts(panel)).noneMatch(text -> text.startsWith("Select a"));
    }

    @Test
    void selectingOneRowShowsItsDetail() {
        ResourceTablePanel panel = connectedWith(resource("docs://a", "alpha"));

        panel.table().setRowSelectionInterval(0, 0);

        assertThat(panel.view().showingDetail()).isTrue();
    }

    @Test
    void theDetailGetsRealSpaceWhenARowIsSelectedAfterStartingHidden() throws Exception {
        ResourceTablePanel panel = connectedWith(resource("docs://a", "alpha"));
        panel.setSize(800, 600);
        layOut(panel);

        panel.table().setRowSelectionInterval(0, 0);
        layOut(panel);

        assertThat(panel.detailPanel().component().getHeight()).isGreaterThan(100);
    }

    @Test
    void theSelectedRowStaysInViewWhenTheDetailOpensBelowIt() throws Exception {
        ResourceTablePanel panel = connectedWith(java.util.stream.IntStream.range(0, 40)
                .mapToObj(index -> resource("docs://" + index, "item " + index))
                .toArray(McpResourceDefinition[]::new));
        panel.setSize(800, 600);
        layOut(panel);

        panel.table().setRowSelectionInterval(39, 39);
        layOut(panel);
        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        java.awt.Rectangle row = panel.table().getCellRect(39, 0, true);
        assertThat(panel.table().getVisibleRect().contains(row)).isTrue();
    }

    @Test
    void theSelectedRowKeepsTheActiveSelectionColourWhenTheTableLosesFocus() {
        javax.swing.JTable table = new ResourceTablePanel().table();

        assertThat(table.getSelectionBackground()).isEqualTo(
                javax.swing.UIManager.getColor("Table.selectionBackground"))
                .isNotInstanceOf(javax.swing.plaf.UIResource.class);
    }

    @Test
    void selectingSeveralRowsHidesTheDetailWithoutAPlaceholder() {
        ResourceTablePanel panel = connectedWith(resource("docs://a", "alpha"), resource("docs://b", "beta"));

        panel.table().setRowSelectionInterval(0, 1);

        assertThat(panel.view().showingDetail()).isFalse();
        assertThat(visibleTexts(panel)).noneMatch(text -> text.contains("highlighted"));
    }

    @Test
    void everyInventoryTypeHidesTheDetailUntilARowIsSelected() {
        ToolTablePanel tools = new ToolTablePanel();
        tools.populate(List.of(new McpToolDefinition("echo", "Echo text", "{}")));
        tools.setAvailability(InventoryView.Availability.CONNECTED);
        PromptTablePanel prompts = new PromptTablePanel();
        prompts.populate(List.of(new com.mcpscanner.mcp.McpPromptDefinition("summarise", "", List.of())));
        prompts.setAvailability(InventoryView.Availability.CONNECTED);
        ResourceTemplateTablePanel templates = new ResourceTemplateTablePanel();
        templates.populate(List.of(new com.mcpscanner.mcp.McpResourceTemplateDefinition("file:///{path}", "files", "", "text/plain")));
        templates.setAvailability(InventoryView.Availability.CONNECTED);

        assertThat(List.of(tools.view(), prompts.view(), templates.view()))
                .noneMatch(InventoryView::showingDetail);
    }

    private static void layOut(java.awt.Container container) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> layOutTree(container));
    }

    private static void layOutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layOutTree(nested);
            }
        }
    }

    private static List<String> visibleTexts(java.awt.Container root) {
        return com.mcpscanner.testutil.SwingTree.visibleDescendants(root).stream()
                .filter(javax.swing.JLabel.class::isInstance)
                .map(c -> ((javax.swing.JLabel) c).getText())
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Test
    void toolsTableSharesTheSameViewAndKeepsItsReadOnlyPreset() {
        ToolTablePanel panel = new ToolTablePanel();
        panel.populate(List.of(new McpToolDefinition("echo", "Echo text", "{}")));
        panel.setAvailability(InventoryView.Availability.CONNECTED);

        panel.view().filterField().setText("nomatch");

        assertThat(panel.view().emptyMessage()).startsWith("No tools match “nomatch”.");
        assertThat(com.mcpscanner.testutil.SwingTree.button(panel, "Select read-only").isVisible()).isTrue();
    }

    @Test
    void spaceTogglesTheTickOfTheSelectedRows() {
        ResourceTablePanel panel = connectedWith(resource("docs://a", "alpha"), resource("docs://b", "beta"));
        javax.swing.JTable table = panel.table();
        table.setRowSelectionInterval(0, 1);

        Object action = table.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                .get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0));
        table.getActionMap().get(action).actionPerformed(null);

        assertThat(panel.selectedResources()).isEmpty();
    }

    @Test
    void shiftF10AndTheMenuKeyOpenTheRowPopup() {
        javax.swing.JTable table = new ResourceTablePanel().table();

        for (javax.swing.KeyStroke trigger : InventoryRowPopup.KEYBOARD_TRIGGERS) {
            assertThat(table.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(trigger)).isEqualTo("showRowPopup");
        }
        assertThat(table.getActionMap().get("showRowPopup")).isNotNull();
    }

    private static ResourceTablePanel connectedWith(McpResourceDefinition... resources) {
        ResourceTablePanel panel = new ResourceTablePanel();
        panel.populate(List.of(resources));
        panel.setAvailability(InventoryView.Availability.CONNECTED);
        return panel;
    }

    private static void untickAll(ResourceTablePanel panel) {
        for (int row = 0; row < panel.table().getModel().getRowCount(); row++) {
            panel.table().getModel().setValueAt(false, row, SelectableInventoryTableModel.SELECT_COLUMN_INDEX);
        }
    }

    private record TableUnderTest(AbstractInventoryTablePanel<?, ?> panel, String plural) {
    }

    private static McpResourceDefinition resource(String uri, String name) {
        return new McpResourceDefinition(uri, name, "", "text/plain");
    }
}
