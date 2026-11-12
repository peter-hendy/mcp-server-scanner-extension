package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRawJsonTest {

    @Test
    void legacyConstructorsGiveAnEmptyObjectRaw() {
        List<JsonNode> raws = List.of(
                new McpToolDefinition("t", "", "{}").raw(),
                new McpToolDefinition("t", "", "{}", List.of()).raw(),
                new McpToolDefinition("t", "", "{}", List.of(), ToolAnnotations.EMPTY).raw(),
                new McpResourceDefinition("u", "n", "d", "m").raw(),
                new McpResourceDefinition("u", "n", "d", "m", List.of()).raw(),
                new McpResourceTemplateDefinition("u", "n", "d", "m").raw(),
                new McpResourceTemplateDefinition("u", "n", "d", "m", List.of()).raw(),
                new McpPromptDefinition("p", "d", List.of()).raw(),
                new McpPromptDefinition("p", "d", List.of(), List.of()).raw(),
                new ServerMetadata(Map.of(), "", Map.of()).raw(),
                new ServerMetadata(Map.of(), "", Map.of(), List.of()).raw(),
                ServerMetadata.empty().raw());

        assertThat(raws).allSatisfy(raw -> assertThat(raw).isEqualTo(emptyObject()));
    }

    @Test
    void nullRawBecomesAnEmptyObject() {
        McpToolDefinition tool = new McpToolDefinition("t", "", "{}", List.of(), ToolAnnotations.EMPTY, null);

        assertThat(tool.raw()).isEqualTo(emptyObject());
    }

    @Test
    void recordsAreNotAliasedToTheReceivedJson() {
        ObjectNode received = entry();
        McpToolDefinition tool = new McpToolDefinition("t", "", "{}", List.of(), ToolAnnotations.EMPTY, received);
        McpResourceDefinition resource = new McpResourceDefinition("u", "n", "d", "m", List.of(), received);
        McpResourceTemplateDefinition template =
                new McpResourceTemplateDefinition("u", "n", "d", "m", List.of(), received);
        McpPromptDefinition prompt = new McpPromptDefinition("p", "d", List.of(), List.of(), received);
        ServerMetadata metadata = new ServerMetadata(Map.of(), "", Map.of(), List.of(), received);

        received.put("name", "changed");

        assertThat(List.of(tool.raw(), resource.raw(), template.raw(), prompt.raw(), metadata.raw()))
                .allSatisfy(raw -> assertThat(raw.get("name").asText()).isEqualTo("original"));
    }

    private static ObjectNode entry() {
        return McpObjectMapper.INSTANCE.createObjectNode().put("name", "original");
    }

    private static ObjectNode emptyObject() {
        return McpObjectMapper.INSTANCE.createObjectNode();
    }
}
