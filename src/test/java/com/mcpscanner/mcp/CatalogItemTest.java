package com.mcpscanner.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogItemTest {

    @Test
    void aToolWithoutANameIsMalformed() {
        McpToolDefinition tool = new McpToolDefinition(null, "d", "{}");

        assertThat(tool.isMalformed()).isTrue();
        assertThat(tool.problems()).containsExactly("Missing required field \"name\"");
    }

    @Test
    void aResourceWithoutAUriIsMalformed() {
        assertThat(new McpResourceDefinition(null, "n", null, null).problems())
                .containsExactly("Missing required field \"uri\"");
    }

    @Test
    void aTemplateWithoutAUriTemplateIsMalformed() {
        assertThat(new McpResourceTemplateDefinition(null, "n", null, null).problems())
                .containsExactly("Missing required field \"uriTemplate\"");
    }

    @Test
    void aPromptWithoutANameIsMalformed() {
        assertThat(new McpPromptDefinition(null, null, List.of()).problems())
                .containsExactly("Missing required field \"name\"");
    }

    @Test
    void itemsWithTheirRequiredFieldAreWellFormed() {
        assertThat(new McpToolDefinition("t", null, "{}").isMalformed()).isFalse();
        assertThat(new McpResourceDefinition("file:///x", null, null, null).isMalformed()).isFalse();
        assertThat(new McpResourceTemplateDefinition("file:///{x}", null, null, null).isMalformed()).isFalse();
        assertThat(new McpPromptDefinition("p", null, List.of()).isMalformed()).isFalse();
    }

    @Test
    void onlyWellFormedItemsAreKept() {
        McpToolDefinition good = new McpToolDefinition("t", null, "{}");

        assertThat(CatalogItem.wellFormed(List.of(good, new McpToolDefinition(null, null, "{}"))))
                .containsExactly(good);
    }
}
