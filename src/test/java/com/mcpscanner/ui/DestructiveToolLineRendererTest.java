package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DestructiveToolLineRendererTest {

    private static final ToolAnnotations DESTRUCTIVE =
            new ToolAnnotations(null, false, true, null, null);

    @Test
    void disablesHtmlForUntrustedToolName() {
        JComponent component = render(tool("<html><img src=x>"));

        assertThat(component.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void showsTheFormattedToolLine() {
        JLabel label = (JLabel) render(tool("destroy"));

        assertThat(label.getText()).isEqualTo("• destroy (destructive)");
    }

    @Test
    void disablesHtmlForAPlainTextLine() {
        JList<String> list = new JList<>(new String[] {"<html>x"});
        JLabel label = (JLabel) new DestructiveToolLineRenderer()
                .getListCellRendererComponent(list, "<html>x", 0, false, false);

        assertThat(label.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(label.getText()).isEqualTo("<html>x");
    }

    private static JComponent render(McpToolDefinition tool) {
        JList<McpToolDefinition> list = new JList<>(new McpToolDefinition[] {tool});
        return (JComponent) new DestructiveToolLineRenderer()
                .getListCellRendererComponent(list, tool, 0, false, false);
    }

    private static McpToolDefinition tool(String name) {
        return new McpToolDefinition(name, "desc", "{}", List.of(), DESTRUCTIVE);
    }
}
