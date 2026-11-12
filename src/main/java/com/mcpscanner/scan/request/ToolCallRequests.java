package com.mcpscanner.scan.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.JsonSchemaDefaults;

import java.util.List;

final class ToolCallRequests implements RequestFamilyBuilder {

    @Override
    public RequestFamily family() {
        return RequestFamily.TOOLS_CALL;
    }

    @Override
    public boolean supportedBy(ServerSurface surface) {
        return !surface.inventory().tools().isEmpty();
    }

    @Override
    public List<McpMessage> build(ServerSurface surface) {
        return surface.inventory().tools().stream().map(ToolCallRequests::toolCall).toList();
    }

    private static McpMessage toolCall(McpToolDefinition tool) {
        ObjectNode params = McpParams.empty().put("name", tool.name());
        params.set("arguments", JsonSchemaDefaults.defaultArguments(tool.inputSchema()));
        return McpMessage.namedRequest(RequestFamily.TOOLS_CALL, McpMethods.TOOLS_CALL, McpParams.withMeta(params),
                tool.name());
    }
}
