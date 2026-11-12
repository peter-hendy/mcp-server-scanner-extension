package com.mcpscanner.scan.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.scan.PromptDefaults;

import java.util.List;

final class PromptGetRequests implements RequestFamilyBuilder {

    @Override
    public RequestFamily family() {
        return RequestFamily.PROMPTS_GET;
    }

    @Override
    public boolean supportedBy(ServerSurface surface) {
        return !surface.inventory().prompts().isEmpty();
    }

    @Override
    public List<McpMessage> build(ServerSurface surface) {
        return surface.inventory().prompts().stream().map(PromptGetRequests::promptGet).toList();
    }

    private static McpMessage promptGet(McpPromptDefinition prompt) {
        ObjectNode params = McpParams.empty().put("name", prompt.name());
        params.set("arguments", PromptDefaults.arguments(prompt));
        return McpMessage.namedRequest(RequestFamily.PROMPTS_GET, McpMethods.PROMPTS_GET, McpParams.withMeta(params),
                prompt.name());
    }
}
