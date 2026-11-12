package com.mcpscanner.scan.request;

import java.util.List;

final class ClientRegistrationRequests implements RequestFamilyBuilder {

    @Override
    public RequestFamily family() {
        return RequestFamily.CLIENT_REGISTRATION;
    }

    @Override
    public boolean supportedBy(ServerSurface surface) {
        return surface.authorizationServer().registrationEndpoint().isPresent();
    }

    @Override
    public List<ClientRegistrationMessage> build(ServerSurface surface) {
        return surface.authorizationServer().registrationEndpoint()
                .map(endpoint -> new ClientRegistrationMessage(endpoint, ClientMetadataDocument.full()))
                .stream()
                .toList();
    }
}
