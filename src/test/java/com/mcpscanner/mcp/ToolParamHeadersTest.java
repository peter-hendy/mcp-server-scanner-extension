package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolParamHeadersTest {

    private static final String TENANT_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"tenant\":{\"type\":\"string\",\"x-mcp-header\":\"Tenant\"}}}";
    private static final JsonNode ARGUMENTS = arguments("{\"tenant\":\"acme\"}");

    private static final String INVALID_SCHEMA = "{\"type\":\"object\",\"properties\":{"
            + "\"tenant\":{\"type\":\"string\",\"x-mcp-header\":\"Tenant\"},"
            + "\"ratio\":{\"type\":\"number\",\"x-mcp-header\":\"Ratio\"},"
            + "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"x-mcp-header\":\"Tag\"}}}}";

    private final List<String> warnings = new java.util.ArrayList<>();
    private final ToolParamHeaders registry = new ToolParamHeaders(warnings::add);

    @Test
    void knowsNothingBeforeLearning() {
        assertThat(registry.forTool("lookup").valuesFor(ARGUMENTS)).isEmpty();
    }

    @Test
    void learnsTheHeadersEachDiscoveredToolDeclares() {
        registry.learn(List.of(new McpToolDefinition("lookup", "", TENANT_SCHEMA),
                new McpToolDefinition("plain", "", "{\"type\":\"object\"}")));

        assertThat(registry.forTool("lookup").valuesFor(ARGUMENTS)).containsEntry("Mcp-Param-Tenant", "acme");
        assertThat(registry.forTool("plain").valuesFor(ARGUMENTS)).isEmpty();
        assertThat(registry.forTool("unknown").valuesFor(ARGUMENTS)).isEmpty();
    }

    @Test
    void replacesEarlierToolsWhenLearningAgain() {
        registry.learn(List.of(new McpToolDefinition("lookup", "", TENANT_SCHEMA)));
        registry.learn(List.of(new McpToolDefinition("other", "", TENANT_SCHEMA)));

        assertThat(registry.forTool("lookup").valuesFor(ARGUMENTS)).isEmpty();
        assertThat(registry.forTool("other").valuesFor(ARGUMENTS)).isNotEmpty();
    }

    @Test
    void warnsOncePerToolAboutTheAnnotationsItSkipsAndKeepsItsValidOnes() {
        McpToolDefinition tool = new McpToolDefinition("lookup", "", INVALID_SCHEMA);

        registry.learn(List.of(tool));
        registry.learn(List.of(tool, new McpToolDefinition("plain", "", TENANT_SCHEMA)));

        assertThat(warnings).containsExactly("Tool lookup: Mcp-Param headers not sent for x-mcp-header Ratio is on "
                + "ratio, which isn't a string, integer or boolean; x-mcp-header Tag is on a value that isn't "
                + "reached through properties alone");
        assertThat(registry.forTool("lookup").valuesFor(ARGUMENTS)).containsEntry("Mcp-Param-Tenant", "acme");
    }

    @Test
    void warnsAgainAfterADisconnect() {
        McpToolDefinition tool = new McpToolDefinition("lookup", "", INVALID_SCHEMA);
        registry.learn(List.of(tool));

        registry.forget();
        registry.learn(List.of(tool));

        assertThat(warnings).hasSize(2);
    }

    @Test
    void forgetsEveryToolOnDisconnect() {
        registry.learn(List.of(new McpToolDefinition("lookup", "", TENANT_SCHEMA)));

        registry.forget();

        assertThat(registry.forTool("lookup").valuesFor(ARGUMENTS)).isEmpty();
    }

    @Test
    void ignoresUnparseableOrMissingSchemasAndNames() {
        registry.learn(List.of(new McpToolDefinition("broken", "", "{not json"),
                new McpToolDefinition("missing", "", null),
                new McpToolDefinition(null, "", TENANT_SCHEMA)));

        assertThat(registry.forTool("broken").valuesFor(ARGUMENTS)).isEmpty();
        assertThat(registry.forTool("missing").valuesFor(ARGUMENTS)).isEmpty();
    }

    private static JsonNode arguments(String json) {
        try {
            return McpObjectMapper.INSTANCE.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
