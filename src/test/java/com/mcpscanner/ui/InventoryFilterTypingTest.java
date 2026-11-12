package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryFilterTypingTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void typingIntoTheToolsFilterHidesNonMatchingTools() throws Exception {
        assertTypingFilters(() -> {
            ToolTablePanel panel = new ToolTablePanel();
            panel.populate(List.of(new McpToolDefinition("search_files", "Find files", "{}"),
                    new McpToolDefinition("delete_files", "Remove files", "{}")));
            panel.setAvailability(InventoryView.Availability.CONNECTED);
            return new Fixture(panel.view(), panel.table());
        }, "delete", 1);
    }

    @Test
    void typingIntoTheResourcesFilterHidesNonMatchingResources() throws Exception {
        assertTypingFilters(() -> {
            ResourceTablePanel panel = new ResourceTablePanel();
            panel.populate(List.of(new McpResourceDefinition("docs://readme", "readme", "", "text/plain"),
                    new McpResourceDefinition("docs://changelog", "changelog", "", "text/plain")));
            panel.setAvailability(InventoryView.Availability.CONNECTED);
            return new Fixture(panel.view(), panel.table());
        }, "changelog", 2);
    }

    @Test
    void typingIntoTheTemplatesFilterHidesNonMatchingTemplates() throws Exception {
        assertTypingFilters(() -> {
            ResourceTemplateTablePanel panel = new ResourceTemplateTablePanel();
            panel.populate(List.of(new McpResourceTemplateDefinition("file:///{path}", "file", "", "text/plain"),
                    new McpResourceTemplateDefinition("user://{id}", "user", "", "text/plain")));
            panel.setAvailability(InventoryView.Availability.CONNECTED);
            return new Fixture(panel.view(), panel.table());
        }, "user", 2);
    }

    @Test
    void typingIntoThePromptsFilterHidesNonMatchingPrompts() throws Exception {
        assertTypingFilters(() -> {
            PromptTablePanel panel = new PromptTablePanel();
            panel.populate(List.of(new McpPromptDefinition("summarise", "", List.of()),
                    new McpPromptDefinition("translate", "", List.of())));
            panel.setAvailability(InventoryView.Availability.CONNECTED);
            return new Fixture(panel.view(), panel.table());
        }, "translate", 1);
    }

    @Test
    void checkboxValuesDoNotMakeEveryRowMatch() throws Exception {
        assertTypingFilters(() -> {
            ResourceTablePanel panel = new ResourceTablePanel();
            panel.populate(List.of(new McpResourceDefinition("docs://a", "alpha", "", "x"),
                    new McpResourceDefinition("docs://b", "omega", "", "x")));
            panel.setAvailability(InventoryView.Availability.CONNECTED);
            return new Fixture(panel.view(), panel.table());
        }, "alpha", 2);
    }

    @Test
    void wordsThatOnlyAppearInDescriptionsOrAnnotationsDoNotKeepEveryToolVisible() throws Exception {
        assertTypingFilters(() -> {
            ToolTablePanel panel = new ToolTablePanel();
            panel.populate(List.of(new McpToolDefinition("read_file", "Read a file", "{}"),
                    new McpToolDefinition("write_file", "Write a file that you read later", "{}")));
            panel.setAvailability(InventoryView.Availability.CONNECTED);
            return new Fixture(panel.view(), panel.table());
        }, "read", 1);
    }

    @Test
    void resourcesMatchOnTheirUriAsWellAsTheirName() throws Exception {
        assertTypingFilters(() -> {
            ResourceTablePanel panel = new ResourceTablePanel();
            panel.populate(List.of(new McpResourceDefinition("docs://readme", "a", "", "text/plain"),
                    new McpResourceDefinition("logs://today", "b", "", "text/plain")));
            panel.setAvailability(InventoryView.Availability.CONNECTED);
            return new Fixture(panel.view(), panel.table());
        }, "logs://", 1);
    }

    private static void assertTypingFilters(Supplier<Fixture> fixtureFactory, String typed, int nameColumn)
            throws Exception {
        List<Object> visible = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixtureFactory.get();
            type(fixture.view().filterField(), typed);
            for (int row = 0; row < fixture.table().getRowCount(); row++) {
                visible.add(fixture.table().getValueAt(row, nameColumn));
            }
        });
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).toString()).contains(typed);
    }

    private static void type(JTextField field, String text) {
        javax.swing.Action keyTyped = field.getKeymap().getDefaultAction();
        for (char character : text.toCharArray()) {
            keyTyped.actionPerformed(new java.awt.event.ActionEvent(field, java.awt.event.ActionEvent.ACTION_PERFORMED,
                    String.valueOf(character)));
        }
        assertThat(field.getText()).as("typed text reached the field").isEqualTo(text);
    }

    private record Fixture(InventoryView view, JTable table) {
    }
}
