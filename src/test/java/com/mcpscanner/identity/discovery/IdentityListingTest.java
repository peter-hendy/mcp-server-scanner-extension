package com.mcpscanner.identity.discovery;

import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ServerMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityListingTest {

    @Test
    void malformedItemsStayOutOfTheListing() {
        ConnectResult catalog = new ConnectResult(
                List.of(new McpToolDefinition("ok", null, "{}"), new McpToolDefinition(null, null, "{}")),
                List.of(new McpResourceDefinition(null, "r", null, null)),
                List.of(new McpResourceTemplateDefinition(null, "t", null, null)),
                List.of(new McpPromptDefinition(null, null, List.of()), new McpPromptDefinition("p", null, List.of())),
                ServerMetadata.empty());

        IdentityListing listing = IdentityListing.of(catalog);

        assertThat(listing.toolNames()).containsExactly("ok");
        assertThat(listing.resourceUris()).isEmpty();
        assertThat(listing.templateUris()).isEmpty();
        assertThat(listing.promptNames()).containsExactly("p");
    }
}
