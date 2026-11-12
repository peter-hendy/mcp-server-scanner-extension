package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.PromptArgument;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryDetailEmptySectionsTest {

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}";

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void anEmptyResourceShowsOnlyItsName() {
        ResourceDetailPanel detail = new ResourceTablePanel().detailPanel();

        detail.show(new McpResourceDefinition("", "Bare", null, null));

        assertThat(OverviewProbe.texts(detail.overview())).isEmpty();
        assertThat(detail.headingLabel().getText()).isEqualTo("Bare");
    }

    @Test
    void aPopulatedResourceShowsEverySection() {
        ResourceDetailPanel detail = new ResourceTablePanel().detailPanel();
        detail.show(new McpResourceDefinition("", "Bare", null, null));

        detail.show(new McpResourceDefinition("docs://a", "Full", "About it", "text/plain"));

        assertThat(OverviewProbe.texts(detail.overview())).contains("About it", "docs://a", "text/plain");
    }

    @Test
    void anEmptyTemplateShowsOnlyItsName() {
        ResourceTemplateDetailPanel detail = new ResourceTemplateTablePanel().detailPanel();

        detail.show(new McpResourceTemplateDefinition(null, "Bare", "  ", ""));

        assertThat(OverviewProbe.texts(detail.overview())).isEmpty();
    }

    @Test
    void aPopulatedTemplateShowsEverySection() {
        ResourceTemplateDetailPanel detail = new ResourceTemplateTablePanel().detailPanel();

        detail.show(new McpResourceTemplateDefinition("file:///{path}", "Full", "About it", "text/plain"));

        assertThat(OverviewProbe.texts(detail.overview())).contains("About it", "file:///{path}", "text/plain");
    }

    @Test
    void anEmptyPromptHasNoDescriptionBoxAndSaysItTakesNoArguments() {
        PromptTablePanel.PromptDetailPanel detail = new PromptTablePanel.PromptDetailPanel();

        detail.show(new McpPromptDefinition("Bare", null, List.of()));

        assertThat(detail.description().isVisible()).isFalse();
        assertThat(detail.argumentsView().takesNoArguments()).isTrue();
    }

    @Test
    void aPromptWithOnlyArgumentsShowsJustTheArguments() {
        PromptTablePanel.PromptDetailPanel detail = new PromptTablePanel.PromptDetailPanel();

        detail.show(new McpPromptDefinition("Args", "", List.of(new PromptArgument("topic", "", true))));

        assertThat(detail.description().isVisible()).isFalse();
        assertThat(detail.argumentsView().takesNoArguments()).isFalse();
    }

    @Test
    void aPopulatedPromptShowsDescriptionAndArguments() {
        PromptTablePanel.PromptDetailPanel detail = new PromptTablePanel.PromptDetailPanel();
        detail.show(new McpPromptDefinition("Bare", null, List.of()));

        detail.show(new McpPromptDefinition("Full", "About it", List.of(new PromptArgument("topic", "", true))));

        assertThat(detail.description().isVisible()).isTrue();
        assertThat(detail.argumentsView().takesNoArguments()).isFalse();
    }

    @Test
    void anEmptyToolHasOnlyTheRawTabAndNoArgumentsHint() {
        ToolDetailPanel detail = new ToolDetailPanel();

        detail.show(new McpToolDefinition("bare", null, "{\"type\":\"object\"}"));

        assertThat(detail.tabs().titles()).containsExactly("Raw");
        assertThat(detail.argumentsHint().isVisible()).isFalse();
    }

    @Test
    void aToolWithOnlyADescriptionHasOnlyThatTab() {
        ToolDetailPanel detail = new ToolDetailPanel();

        detail.show(new McpToolDefinition("described", "Does things", null));

        assertThat(detail.tabs().titles()).containsExactly("Description", "Raw");
        assertThat(detail.argumentsHint().isVisible()).isFalse();
    }

    @Test
    void aPopulatedToolShowsDescriptionParametersAndTheHint() {
        ToolDetailPanel detail = new ToolDetailPanel();
        detail.show(new McpToolDefinition("bare", null, null));

        detail.show(new McpToolDefinition("full", "Does things", SCHEMA));

        assertThat(detail.tabs().titles()).containsExactly("Description", "Parameters (1)", "Raw");
        assertThat(detail.argumentsHint().isVisible()).isTrue();
    }
}
