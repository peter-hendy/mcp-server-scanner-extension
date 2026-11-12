package com.mcpscanner.cvetargets.requests;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;

import java.net.URI;

public record ArmEndpoint(URI uri, AuthStrategy auth, TransportType transport) {

    public ArmEndpoint(URI uri, AuthStrategy auth) {
        this(uri, auth, TransportType.AUTO);
    }

    public McpServerConfig serverConfig() {
        return new McpServerConfig(uri.toString(), transport, auth);
    }
}
