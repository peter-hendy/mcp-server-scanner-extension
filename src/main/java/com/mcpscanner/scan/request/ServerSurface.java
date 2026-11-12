package com.mcpscanner.scan.request;

import com.mcpscanner.scan.ScanInventory;

public record ServerSurface(ScanInventory inventory, ServerCapabilities capabilities, String protocolVersion,
                            AdvertisedAuthorizationServer authorizationServer) {

    public ServerSurface {
        authorizationServer = authorizationServer == null ? AdvertisedAuthorizationServer.none() : authorizationServer;
    }

    public ServerSurface(ScanInventory inventory, ServerCapabilities capabilities, String protocolVersion) {
        this(inventory, capabilities, protocolVersion, AdvertisedAuthorizationServer.none());
    }

    public ServerSurface(ScanInventory inventory, ServerCapabilities capabilities) {
        this(inventory, capabilities, null);
    }
}
