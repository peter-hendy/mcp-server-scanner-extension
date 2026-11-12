package com.mcpscanner.scan.request;

import com.mcpscanner.mcp.McpMethods;

import java.util.List;

final class SubscriptionRequests implements RequestFamilyBuilder {

    @Override
    public RequestFamily family() {
        return RequestFamily.SUBSCRIBE;
    }

    @Override
    public boolean supportedBy(ServerSurface surface) {
        return surface.capabilities().advertisesResourceSubscription();
    }

    @Override
    public List<McpMessage> build(ServerSurface surface) {
        return ResourceUriMessages.firstConcreteUri(surface.inventory())
                .map(uri -> List.of(
                        ResourceUriMessages.onUri(RequestFamily.SUBSCRIBE, McpMethods.RESOURCES_SUBSCRIBE, uri),
                        ResourceUriMessages.onUri(RequestFamily.SUBSCRIBE, McpMethods.RESOURCES_UNSUBSCRIBE, uri)))
                .orElseGet(List::of);
    }
}
