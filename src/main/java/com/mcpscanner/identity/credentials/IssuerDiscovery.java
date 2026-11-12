package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;

import java.net.URI;

@FunctionalInterface
public interface IssuerDiscovery {

    DiscoveredMetadata discover(URI mcpResource);
}
