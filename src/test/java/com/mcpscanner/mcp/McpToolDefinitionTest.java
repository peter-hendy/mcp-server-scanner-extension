package com.mcpscanner.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolDefinitionTest {

    @Test
    void recordAccessorsReturnConstructorValues() {
        McpToolDefinition tool = new McpToolDefinition("myTool", "A description", "{\"type\":\"object\"}");

        assertThat(tool.name()).isEqualTo("myTool");
        assertThat(tool.description()).isEqualTo("A description");
        assertThat(tool.inputSchema()).isEqualTo("{\"type\":\"object\"}");
    }
}
