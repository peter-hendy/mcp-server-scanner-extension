package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DestructiveScanPolicyTest {

    private static final ToolAnnotations READ_ONLY =
            new ToolAnnotations(null, true, null, null, null);
    private static final ToolAnnotations DESTRUCTIVE =
            new ToolAnnotations(null, false, true, null, null);

    @Test
    void requiresConfirmationWhenSelectionIncludesDestructiveTool() {
        List<McpToolDefinition> selected = List.of(
                tool("read", READ_ONLY),
                tool("destroy", DESTRUCTIVE));

        assertThat(DestructiveScanPolicy.requiresConfirmation(selected, false))
                .isTrue();
    }

    @Test
    void requiresConfirmationWhenSelectionIncludesAnnotationsMissingTool() {
        List<McpToolDefinition> selected = List.of(
                tool("read", READ_ONLY),
                tool("unknown", ToolAnnotations.EMPTY));

        assertThat(DestructiveScanPolicy.requiresConfirmation(selected, false))
                .isTrue();
    }

    @Test
    void noConfirmationWhenAllSelectedAreReadOnly() {
        List<McpToolDefinition> selected = List.of(
                tool("a", READ_ONLY),
                tool("b", READ_ONLY));

        assertThat(DestructiveScanPolicy.requiresConfirmation(selected, false))
                .isFalse();
    }

    @Test
    void noConfirmationWhenDontAskAgainSet() {
        List<McpToolDefinition> selected = List.of(
                tool("destroy", DESTRUCTIVE));

        assertThat(DestructiveScanPolicy.requiresConfirmation(selected, true))
                .isFalse();
    }

    @Test
    void nonReadOnlyReturnsOnlyDestructiveAndUnannotated() {
        McpToolDefinition read = tool("read", READ_ONLY);
        McpToolDefinition destroy = tool("destroy", DESTRUCTIVE);
        McpToolDefinition unknown = tool("unknown", ToolAnnotations.EMPTY);

        List<McpToolDefinition> result = DestructiveScanPolicy.nonReadOnly(
                List.of(read, destroy, unknown));

        assertThat(result).containsExactly(destroy, unknown);
    }

    @Test
    void formatToolLineLabelsDestructive() {
        assertThat(DestructiveScanPolicy.formatToolLine(tool("destroy", DESTRUCTIVE)))
                .isEqualTo("• destroy (destructive)");
    }

    @Test
    void formatToolLineLabelsAnnotationsMissing() {
        assertThat(DestructiveScanPolicy.formatToolLine(tool("unknown", ToolAnnotations.EMPTY)))
                .isEqualTo("• unknown (annotations missing)");
    }

    private static McpToolDefinition tool(String name, ToolAnnotations annotations) {
        return new McpToolDefinition(name, "desc", "{}", List.of(), annotations);
    }

    @Test
    void countLineSaysHowManyOfTheSelectedToolsNeedConfirmation() {
        assertThat(DestructiveScanPolicy.countLine(2, 5))
                .isEqualTo("2 of the 5 selected tools are not annotated read-only:");
        assertThat(DestructiveScanPolicy.countLine(1, 1))
                .isEqualTo("1 of the 1 selected tool is not annotated read-only:");
    }
}
