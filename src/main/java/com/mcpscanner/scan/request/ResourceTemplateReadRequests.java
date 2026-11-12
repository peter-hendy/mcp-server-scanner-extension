package com.mcpscanner.scan.request;

import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.scan.UriTemplateExpansion;

import java.util.List;

final class ResourceTemplateReadRequests implements RequestFamilyBuilder {

    @Override
    public RequestFamily family() {
        return RequestFamily.RESOURCE_TEMPLATES_READ;
    }

    @Override
    public boolean supportedBy(ServerSurface surface) {
        return !surface.inventory().resourceTemplates().isEmpty();
    }

    @Override
    public List<McpMessage> build(ServerSurface surface) {
        return surface.inventory().resourceTemplates().stream()
                .map(McpResourceTemplateDefinition::uriTemplate)
                .map(UriTemplateExpansion::expand)
                .map(expansion -> ResourceUriMessages.read(RequestFamily.RESOURCE_TEMPLATES_READ,
                        expansion.expandedUri()).withTemplateExpansion(expansion))
                .toList();
    }
}
