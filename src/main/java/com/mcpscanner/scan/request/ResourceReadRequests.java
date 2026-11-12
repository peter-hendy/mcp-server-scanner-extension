package com.mcpscanner.scan.request;

import com.mcpscanner.mcp.McpResourceDefinition;

import java.util.List;

final class ResourceReadRequests implements RequestFamilyBuilder {

    @Override
    public RequestFamily family() {
        return RequestFamily.RESOURCES_READ;
    }

    @Override
    public boolean supportedBy(ServerSurface surface) {
        return !surface.inventory().resources().isEmpty();
    }

    @Override
    public List<McpMessage> build(ServerSurface surface) {
        return surface.inventory().resources().stream()
                .map(McpResourceDefinition::uri)
                .map(uri -> ResourceUriMessages.read(RequestFamily.RESOURCES_READ, uri))
                .toList();
    }
}
