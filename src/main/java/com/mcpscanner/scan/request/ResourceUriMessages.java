package com.mcpscanner.scan.request;

import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.UriTemplateExpansion;

import java.util.Optional;

final class ResourceUriMessages {

    private ResourceUriMessages() {}

    static McpMessage read(RequestFamily family, String uri) {
        return McpMessage.namedRequest(family, McpMethods.RESOURCES_READ, uriParams(uri), uri);
    }

    static McpMessage onUri(RequestFamily family, String method, String uri) {
        return McpMessage.request(family, method, uriParams(uri));
    }

    static Optional<String> firstConcreteUri(ScanInventory inventory) {
        return inventory.resources().stream().map(resource -> resource.uri()).findFirst()
                .or(() -> inventory.resourceTemplates().stream()
                        .map(template -> UriTemplateExpansion.expand(template.uriTemplate()).expandedUri())
                        .findFirst());
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode uriParams(String uri) {
        return McpParams.withMeta(McpParams.empty().put("uri", uri));
    }
}
