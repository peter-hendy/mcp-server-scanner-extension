package com.mcpscanner.scan.request;

import java.net.URI;
import java.util.Optional;

@FunctionalInterface
public interface AdvertisedAuthorizationServer {

    Optional<URI> registrationEndpoint();

    static AdvertisedAuthorizationServer none() {
        return Optional::empty;
    }
}
