package com.mcpscanner.ui.connect;

import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;
import com.mcpscanner.scan.request.AdvertisedAuthorizationServer;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;

import java.net.URI;
import java.util.Optional;

record DiscoveredAuthorizationServer(DiscoveredMetadata metadata) implements AdvertisedAuthorizationServer {

    static AdvertisedAuthorizationServer of(DiscoveredMetadata metadata) {
        return metadata == null ? AdvertisedAuthorizationServer.none() : new DiscoveredAuthorizationServer(metadata);
    }

    @Override
    public Optional<URI> registrationEndpoint() {
        return Optional.ofNullable(metadata.asMetadata()).map(AuthorizationServerMetadata::getRegistrationEndpointURI);
    }
}
