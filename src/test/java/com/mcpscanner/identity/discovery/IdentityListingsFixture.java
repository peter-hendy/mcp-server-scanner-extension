package com.mcpscanner.identity.discovery;

import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class IdentityListingsFixture {

    private IdentityListingsFixture() {
    }

    public static void listed(IdentityListings listings, String principalId, Set<String> tools, Set<String> resources) {
        Map<String, McpToolDefinition> toolMap = new LinkedHashMap<>();
        tools.forEach(name -> toolMap.put(name, new McpToolDefinition(name, name, "{}")));
        Map<String, McpResourceDefinition> resourceMap = new LinkedHashMap<>();
        resources.forEach(uri -> resourceMap.put(uri, new McpResourceDefinition(uri, uri, "", "text/plain")));
        listings.record(principalId, new DiscoveryOutcome.Listed(new IdentityListing(toolMap, resourceMap, Set.of(),
                Set.of())));
    }

    public static void failed(IdentityListings listings, String principalId, String reason) {
        listings.record(principalId, new DiscoveryOutcome.Failed(reason));
    }
}
