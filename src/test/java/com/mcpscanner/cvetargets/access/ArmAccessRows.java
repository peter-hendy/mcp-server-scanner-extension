package com.mcpscanner.cvetargets.access;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.access.AccessCell;
import com.mcpscanner.access.AccessItem;
import com.mcpscanner.access.AccessRow;
import com.mcpscanner.access.ListingState;
import com.mcpscanner.checks.api.AccessExpectations;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.cvetargets.mining.MiningConnection;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.ScanTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ArmAccessRows {

    private final Map<AccessItemKey, SeedRequest> itemSeeds;
    private final MiningConnection connection;

    ArmAccessRows(Map<AccessItemKey, SeedRequest> itemSeeds, MiningConnection connection) {
        this.itemSeeds = Map.copyOf(itemSeeds);
        this.connection = connection;
    }

    List<AccessRow> rows(PrincipalRoster roster, AccessExpectations expectations) {
        Map<AccessItemKey, AccessItem> items = new LinkedHashMap<>();
        ScanInventory inventory = connection.inventory();
        inventory.tools().forEach(tool -> items.put(AccessItem.tool(tool).key(), AccessItem.tool(tool)));
        inventory.resources().forEach(resource -> items.put(AccessItem.resource(resource).key(),
                AccessItem.resource(resource)));
        itemSeeds.keySet().forEach(key -> items.putIfAbsent(key, declared(key)));
        return items.values().stream().filter(this::confirmable).map(item -> row(item, roster, expectations))
                .toList();
    }

    private boolean confirmable(AccessItem item) {
        return !item.writes() || itemSeeds.containsKey(item.key());
    }

    HttpRequest ownerRequest(ScanTarget target) {
        return Optional.ofNullable(itemSeeds.get(keyOf(target))).map(connection::requestFor)
                .orElseGet(() -> connection.requestFor(target));
    }

    private AccessRow row(AccessItem item, PrincipalRoster roster, AccessExpectations expectations) {
        AccessCell unchecked = AccessCell.of(ListingState.NOT_DISCOVERED, Optional.empty(), false);
        return new AccessRow(item, owner(item.key(), roster), expectations.expectationFor(item.key()), false,
                Map.of(), unchecked);
    }

    private Principal owner(AccessItemKey key, PrincipalRoster roster) {
        Optional<String> routedAs = Optional.ofNullable(itemSeeds.get(key)).map(SeedRequest::asIdentity);
        return routedAs.flatMap(id -> roster.compared().stream().filter(principal -> principal.id().equals(id))
                .findFirst()).orElse(roster.primary());
    }

    private static AccessItem declared(AccessItemKey key) {
        return switch (key.kind()) {
            case TOOL -> AccessItem.tool(new McpToolDefinition(key.name(), "", "{}"));
            case RESOURCE -> AccessItem.resource(new McpResourceDefinition(key.name(), key.name(), "", null));
        };
    }

    private static AccessItemKey keyOf(ScanTarget target) {
        if (target instanceof ScanTarget.Tool tool) {
            return new AccessItemKey(AccessItemKey.Kind.TOOL, tool.tool().name());
        }
        if (target instanceof ScanTarget.Resource resource) {
            return new AccessItemKey(AccessItemKey.Kind.RESOURCE, resource.resource().uri());
        }
        throw new IllegalArgumentException("Check access compares tools and resources, not " + target);
    }
}
