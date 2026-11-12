package com.mcpscanner.scan;

import com.mcpscanner.scan.request.AdvertisedAuthorizationServer;
import com.mcpscanner.scan.request.GeneratedScan;
import com.mcpscanner.scan.request.RequestFamilyToggles;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ServerCapabilities;
import com.mcpscanner.scan.request.ServerSurface;

import com.mcpscanner.mcp.McpToolDefinition;

import java.util.List;
import java.util.function.Supplier;

public final class ScanPlanner {

    private final RequestGenerator generator = RequestGenerator.standard();
    private final RequestFamilyToggles toggles;
    private final Supplier<ServerCapabilities> capabilities;
    private final Supplier<String> protocolVersion;
    private final Supplier<AdvertisedAuthorizationServer> authorizationServer;
    private final OwnerArgumentSource ownedObjects;

    public ScanPlanner(RequestFamilyToggles toggles, Supplier<ServerCapabilities> capabilities,
                Supplier<String> protocolVersion, Supplier<AdvertisedAuthorizationServer> authorizationServer,
                OwnerArgumentSource ownedObjects) {
        this.toggles = toggles;
        this.capabilities = capabilities;
        this.protocolVersion = protocolVersion;
        this.authorizationServer = authorizationServer;
        this.ownedObjects = ownedObjects;
    }

    public GeneratedScan planSelection(ScanInventory selection) {
        return generator.scanAll(new ServerSurface(owned(selection), capabilities.get(), protocolVersion.get(),
                authorizationServer.get()), toggles);
    }

    public GeneratedScan planItem(ScanTarget target) {
        return generator.scanThisRequest(target.family(), owned(target.inventory()));
    }

    public MinedArguments minedArguments(List<McpToolDefinition> tools) {
        return ownedObjects.minedArguments(tools);
    }

    private ScanInventory owned(ScanInventory inventory) {
        return OwnedArguments.apply(inventory, ownedObjects);
    }
}
