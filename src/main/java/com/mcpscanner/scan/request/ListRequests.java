package com.mcpscanner.scan.request;

import com.mcpscanner.scan.ScanInventory;

import java.util.List;
import java.util.function.Function;

final class ListRequests implements RequestFamilyBuilder {

    static final String CURSOR = "mcp-scanner-cursor";

    private final RequestFamily family;
    private final String method;
    private final String capability;
    private final Function<ScanInventory, List<?>> discoveredItems;

    ListRequests(RequestFamily family, String method, String capability,
                 Function<ScanInventory, List<?>> discoveredItems) {
        this.family = family;
        this.method = method;
        this.capability = capability;
        this.discoveredItems = discoveredItems;
    }

    @Override
    public RequestFamily family() {
        return family;
    }

    @Override
    public boolean supportedBy(ServerSurface surface) {
        return surface.capabilities().advertises(capability)
                || !discoveredItems.apply(surface.inventory()).isEmpty();
    }

    @Override
    public List<McpMessage> build(ServerSurface surface) {
        return List.of(McpMessage.request(family, method, McpParams.withMeta(McpParams.empty().put("cursor", CURSOR))));
    }
}
